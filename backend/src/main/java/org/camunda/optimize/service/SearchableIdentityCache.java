/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static org.apache.lucene.search.SortField.STRING_LAST;

@Slf4j
public class SearchableIdentityCache implements AutoCloseable {
  private final long maxEntryLimit;
  private final ByteBuffersDirectory memoryDirectory;
  private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

  private final AtomicLong entryCount = new AtomicLong(0);

  @SneakyThrows(IOException.class)
  public SearchableIdentityCache(final long maxEntryLimit) {
    this.maxEntryLimit = maxEntryLimit;
    this.memoryDirectory = new ByteBuffersDirectory();
    // this is needed for the directory to be accessible by a reader in cases where no write has happened yet
    new IndexWriter(memoryDirectory, new IndexWriterConfig(getIndexAnalyzer())).close();
  }

  @Override
  public void close() {
    doWithWriteLock(() -> {
      try {
        this.memoryDirectory.close();
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed closing lucene in memory directory.", e);
      }
    });
  }

  public void addIdentity(@NonNull final IdentityDto identity) throws MaxEntryLimitHitException {
    doWithWriteLock(() -> {
      enforceMaxEntryLimit(1);
      try (final IndexWriter indexWriter =
             new IndexWriter(memoryDirectory, new IndexWriterConfig(getIndexAnalyzer()))) {
        writeIdentityDto(indexWriter, identity);
        entryCount.incrementAndGet();
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed writing identity [id:" + identity.getId() + "].", e);
      }
    });
  }

  public void addIdentities(@NonNull final List<? extends IdentityDto> identities) throws MaxEntryLimitHitException {
    if (identities.isEmpty()) {
      return;
    }

    doWithWriteLock(() -> {
      enforceMaxEntryLimit(identities.size());
      try (final IndexWriter indexWriter = new IndexWriter(
        memoryDirectory,
        new IndexWriterConfig(getIndexAnalyzer())
      )) {
        identities.forEach(identity -> writeIdentityDto(indexWriter, identity));
        entryCount.addAndGet(identities.size());
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed writing identities.", e);
      }
    });
  }

  public Optional<UserDto> getUserIdentityById(final String id) {
    return getTypedIdentityDtoById(id, IdentityType.USER, SearchableIdentityCache::mapDocumentToUserDto);
  }

  public Optional<GroupDto> getGroupIdentityById(final String id) {
    return getTypedIdentityDtoById(id, IdentityType.GROUP, SearchableIdentityCache::mapDocumentToGroupDto);
  }

  public IdentitySearchResultDto searchIdentities(final String terms) {
    return searchIdentities(terms, 10);
  }

  public IdentitySearchResultDto searchIdentities(final String terms, final int resultLimit) {
    final IdentitySearchResultDto result = new IdentitySearchResultDto();
    doWithReadLock(() -> {
      try (final IndexReader indexReader = DirectoryReader.open(memoryDirectory)) {
        final IndexSearcher searcher = new IndexSearcher(indexReader);

        final SortField nameSort = new SortField(IdentityDto.Fields.name.name(), SortField.Type.STRING, false);
        nameSort.setMissingValue(STRING_LAST);
        final Sort scoreThanNameSort = new Sort(SortField.FIELD_SCORE, nameSort);
        final TopDocs topDocs = searcher.search(
          createSearchIdentityQuery(terms), resultLimit, scoreThanNameSort, true, false
        );

        result.setTotal(topDocs.totalHits);
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
          final Document document = searcher.doc(scoreDoc.doc);
          final IdentityDto identityDto = mapDocumentToIdentityDto(document);
          result.getResult().add(identityDto);
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed searching for identities with terms [id:" + terms + "].", e);
      }
    });
    return result;
  }

  @VisibleForTesting
  public long getCacheSizeInBytes() {
    AtomicLong size = new AtomicLong();
    doWithReadLock(() -> {
      try {
        size.set(
          Arrays.stream(memoryDirectory.listAll())
            .map(file -> {
              try {
                return memoryDirectory.fileLength(file);
              } catch (IOException e) {
                log.error("Failed reading file from in memory directory.", e);
                return 0L;
              }
            })
            .reduce(Long::sum)
            .orElse(0L)
        );
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed getting size of lucene directory.", e);
      }
    });
    return size.get();
  }

  private void enforceMaxEntryLimit(int newRecordCount) throws MaxEntryLimitHitException {
    if (entryCount.get() + newRecordCount > maxEntryLimit) {
      throw new MaxEntryLimitHitException();
    }
  }

  private void doWithReadLock(final Runnable readOperation) {
    readWriteLock.readLock().lock();
    try {
      readOperation.run();
    } finally {
      readWriteLock.readLock().unlock();
    }
  }

  private void doWithWriteLock(final Runnable writeOperation) {
    readWriteLock.writeLock().lock();
    try {
      writeOperation.run();
    } finally {
      readWriteLock.writeLock().unlock();
    }
  }

  private <T extends IdentityDto> Optional<T> getTypedIdentityDtoById(final String id,
                                                                      final IdentityType identityType,
                                                                      final Function<Document, T> mapperFunction) {
    AtomicReference<T> result = new AtomicReference<>();
    doWithReadLock(() -> {
      try (final IndexReader indexReader = DirectoryReader.open(memoryDirectory)) {
        final IndexSearcher searcher = new IndexSearcher(indexReader);
        final BooleanQuery.Builder searchBuilder = new BooleanQuery.Builder();
        searchBuilder.add(
          new TermQuery(new Term(IdentityDto.Fields.id.name(), id)),
          BooleanClause.Occur.MUST
        );
        searchBuilder.add(
          new TermQuery(new Term(IdentityDto.Fields.type.name(), identityType.name())), BooleanClause.Occur.MUST
        );
        final TopDocs topDocs = searcher.search(searchBuilder.build(), 1);
        if (topDocs.totalHits > 0) {
          result.set(mapperFunction.apply(searcher.doc(topDocs.scoreDocs[0].doc)));
        }
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed getting identity by [id:" + id + "].", e);
      }
    });
    return Optional.ofNullable(result.get());
  }

  @SneakyThrows
  private long writeIdentityDto(final IndexWriter indexWriter, final IdentityDto identity) {
    return indexWriter.updateDocument(
      new Term(IdentityDto.Fields.id.name(), identity.getId()), mapIdentityDtoToDocument(identity)
    );
  }

  private BooleanQuery createSearchIdentityQuery(final String searchQuery) {
    final List<String> searchTerms = tokenizeSearchQuery(searchQuery);

    final String[] termsArray = searchTerms.toArray(new String[]{});
    final BooleanQuery.Builder searchBuilder = new BooleanQuery.Builder();

    if (StringUtils.isNotEmpty(searchQuery)) {
      searchBuilder.setMinimumNumberShouldMatch(1);

      // explicit to lowercase field ofr id for exact match ignoring case
      final String allLowerCaseIdField = getAllLowerCaseFieldForDtoField(IdentityDto.Fields.id.name());
      searchBuilder.add(
        new PrefixQuery(new Term(allLowerCaseIdField, searchQuery.toLowerCase())),
        BooleanClause.Occur.SHOULD
      );
      // exact id matches are boosted
      searchBuilder.add(
        // as there are 2 other field clauses and this one should win even if all the others match
        // as one other field has boost of 3, boost by 4 + 2 to let this matcher always win on scoring
        // explicit to lowercase as we also do to lowercase on insert, for cheap case insensitivity
        new BoostQuery(new TermQuery(new Term(allLowerCaseIdField, searchQuery.toLowerCase())), 6),
        BooleanClause.Occur.SHOULD
      );

      // do phrase query on name as it may consist of multiple terms and phrase matches are to be preferred
      // boost it by 3 as it is more important than a prefix email and prefix id match
      searchBuilder.add(
        new BoostQuery(new PhraseQuery(getNgramFieldForDtoField(IdentityDto.Fields.name.name()), termsArray), 3),
        BooleanClause.Occur.SHOULD
      );

      searchBuilder.add(
        // explicit to lowercase as we also do to lowercase on insert, for cheap case insensitivity
        new PrefixQuery(
          new Term(getAllLowerCaseFieldForDtoField(UserDto.Fields.email.name()), searchQuery.toLowerCase())
        ),
        BooleanClause.Occur.SHOULD
      );
    } else {
      searchBuilder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
    }
    return searchBuilder.build();
  }

  @SneakyThrows(IOException.class)
  private static List<String> tokenizeSearchQuery(final String searchTerms) {
    final List<String> tokens = new ArrayList<>();
    try (TokenStream tokenStream = getSearchTermAnalyzer().tokenStream(null, new StringReader(searchTerms))) {
      tokenStream.reset();
      while (tokenStream.incrementToken()) {
        tokens.add(tokenStream.getAttribute(CharTermAttribute.class).toString());
      }
    }
    return tokens;
  }

  @SneakyThrows(IOException.class)
  private static Analyzer getIndexAnalyzer() {
    return CustomAnalyzer.builder()
      .withTokenizer("whitespace")
      .addTokenFilter("lowercase")
      .addTokenFilter("ngram", "minGramSize", "1", "maxGramSize", "16")
      .build();
  }

  @SneakyThrows(IOException.class)
  private static Analyzer getSearchTermAnalyzer() {
    return CustomAnalyzer.builder()
      .withTokenizer("whitespace")
      .addTokenFilter("lowercase")
      .build();
  }

  private static Document mapIdentityDtoToDocument(final IdentityDto identity) {
    final Document document = new Document();

    document.add(new StringField(IdentityDto.Fields.id.name(), identity.getId(), Field.Store.YES));
    // this all lowercase id field is used for cheap case insensitive full term/prefix search
    document.add(new StringField(
      getAllLowerCaseFieldForDtoField(IdentityDto.Fields.id.name()), identity.getId().toLowerCase(), Field.Store.NO)
    );
    document.add(new StringField(IdentityDto.Fields.type.name(), identity.getType().name(), Field.Store.YES));
    Optional.ofNullable(identity.getName()).ifPresent(name -> {
      // as we want to use custom sorting based on name we need to store the name value as sorted doc field
      document.add(new SortedDocValuesField(IdentityDto.Fields.name.name(), new BytesRef(name.toLowerCase())));
      document.add(new StringField(IdentityDto.Fields.name.name(), name, Field.Store.YES));
      document.add(
        new TextField(getNgramFieldForDtoField(IdentityDto.Fields.name.name()), name.toLowerCase(), Field.Store.YES)
      );
    });

    if (identity instanceof UserDto) {
      final UserDto userDto = (UserDto) identity;
      Optional.ofNullable(userDto.getFirstName()).ifPresent(
        firstName -> document.add(new StringField(UserDto.Fields.firstName.name(), firstName, Field.Store.YES))
      );
      Optional.ofNullable(userDto.getLastName()).ifPresent(
        lastName -> document.add(new StringField(UserDto.Fields.lastName.name(), lastName, Field.Store.YES))
      );
      Optional.ofNullable(userDto.getEmail()).ifPresent(email -> {
        document.add(new StringField(UserDto.Fields.email.name(), email, Field.Store.YES));
        // this all lowercase id field is used for cheap case insensitive full term/prefix search
        document.add(new StringField(
          getAllLowerCaseFieldForDtoField(UserDto.Fields.email.name()), email.toLowerCase(), Field.Store.NO)
        );
      });
    }
    return document;
  }

  private static String getAllLowerCaseFieldForDtoField(final String fieldName) {
    return fieldName + ".allLowerCase";
  }

  private static String getNgramFieldForDtoField(final String fieldName) {
    return fieldName + ".ngram";
  }

  private static IdentityDto mapDocumentToIdentityDto(final Document document) {
    final IdentityType identityType = IdentityType.valueOf(document.get(IdentityDto.Fields.type.name()));
    switch (identityType) {
      case USER:
        return mapDocumentToUserDto(document);
      case GROUP:
        return mapDocumentToGroupDto(document);
      default:
        throw new OptimizeRuntimeException("Unsupported identity type :" + identityType.name() + ".");
    }
  }

  private static GroupDto mapDocumentToGroupDto(final Document document) {
    return new GroupDto(
      document.get(IdentityDto.Fields.id.name()),
      document.get(IdentityDto.Fields.name.name())
    );
  }

  private static UserDto mapDocumentToUserDto(final Document document) {
    return new UserDto(
      document.get(IdentityDto.Fields.id.name()),
      document.get(UserDto.Fields.firstName.name()),
      document.get(UserDto.Fields.lastName.name()),
      document.get(UserDto.Fields.email.name())
    );
  }

}
