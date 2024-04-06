/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ConversionUtils.STRING_TO_LONG;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class CollectionUtil {

  public static <K, V> V getOrDefaultForNullValue(Map<K, V> map, K key, V defaultValue) {
    final V value = map.get(key);
    return value == null ? defaultValue : value;
  }

  @SafeVarargs
  public static <T> List<T> throwAwayNullElements(T... array) {
    final List<T> listOfNotNulls = new ArrayList<>();
    for (T o : array) {
      if (o != null) {
        listOfNotNulls.add(o);
      }
    }
    return listOfNotNulls;
  }

  public static <T> List<T> emptyListWhenNull(List<T> aList) {
    return aList == null ? Collections.emptyList() : aList;
  }

  @SuppressWarnings("unchecked")
  public static <T> List<T> withoutNulls(Collection<T> aCollection) {
    if (aCollection != null) {
      return filter(aCollection, obj -> obj != null);
    }
    return Collections.EMPTY_LIST;
  }

  public static <T, S> Map<T, List<S>> addToMap(Map<T, List<S>> map, T key, S value) {
    map.computeIfAbsent(key, k -> new ArrayList<S>()).add(value);
    return map;
  }

  public static Map<String, Object> asMap(Object... keyValuePairs) {
    if (keyValuePairs == null || keyValuePairs.length % 2 != 0) {
      throw new TasklistRuntimeException("keyValuePairs should not be null and has a even length.");
    }
    final Map<String, Object> result = new HashMap<String, Object>();
    for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
      result.put(keyValuePairs[i].toString(), keyValuePairs[i + 1]);
    }
    return result;
  }

  // We need this because map::getOrDefault throws an exception for null keys :-(
  public static <K, T> T getOrDefaultFromMap(Map<K, T> map, K key, T defaultValue) {
    return key == null ? defaultValue : map.getOrDefault(key, defaultValue);
  }

  public static <T> T firstOrDefault(List<T> list, T defaultValue) {
    return list.isEmpty() ? defaultValue : list.get(0);
  }

  public static <S, T> List<T> map(Collection<S> sourceList, Function<S, T> mapper) {
    return map(sourceList.stream(), mapper);
  }

  public static <S, T> List<T> map(S[] sourceArray, Function<S, T> mapper) {
    return map(Arrays.stream(sourceArray).parallel(), mapper);
  }

  public static <S, T> List<T> map(Stream<S> sequenceStream, Function<S, T> mapper) {
    return sequenceStream.map(mapper).collect(Collectors.toList());
  }

  public static <T> List<T> filter(Collection<T> collection, Predicate<T> predicate) {
    return filter(collection.stream(), predicate);
  }

  public static <T> List<T> filter(Stream<T> filterStream, Predicate<T> predicate) {
    return filterStream.filter(predicate).collect(Collectors.toList());
  }

  public static List<String> toSafeListOfStrings(Collection<?> aCollection) {
    return map(withoutNulls(aCollection), obj -> obj.toString());
  }

  public static String[] toSafeArrayOfStrings(Collection<?> aCollection) {
    return toSafeListOfStrings(aCollection).toArray(new String[] {});
  }

  public static List<String> toSafeListOfStrings(Object... objects) {
    return toSafeListOfStrings(Arrays.asList(objects));
  }

  public static List<Long> toSafeListOfLongs(Collection<String> aCollection) {
    return map(withoutNulls(aCollection), STRING_TO_LONG);
  }

  public static <T> void addNotNull(Collection<T> collection, T object) {
    if (collection != null && object != null) {
      collection.add(object);
    }
  }

  public static List<Integer> fromTo(int from, int to) {
    final List<Integer> result = new ArrayList<>();
    for (int i = from; i <= to; i++) {
      result.add(i);
    }
    return result;
  }

  public static boolean isNotEmpty(Collection<?> aCollection) {
    return aCollection != null && !aCollection.isEmpty();
  }

  /**
   * @param subsetId starts from 0
   */
  public static <E> List<E> splitAndGetSublist(List<E> list, int subsetCount, int subsetId) {
    if (subsetId >= subsetCount) {
      return new ArrayList<>();
    }
    final Integer size = list.size();
    final int bucketSize = (int) Math.round((double) size / (double) subsetCount);
    final int start = bucketSize * subsetId;
    final int end;
    if (subsetId == subsetCount - 1) {
      end = size;
    } else {
      end = start + bucketSize;
    }
    return new ArrayList<>(list.subList(start, end));
  }

  public static <T> T chooseOne(List<T> items) {
    return items.get(new Random().nextInt(items.size()));
  }

  public static <T> boolean allElementsAreOfType(Class clazz, T... array) {
    for (T element : array) {
      if (!clazz.isInstance(element)) {
        return false;
      }
    }
    return true;
  }

  public static String[] toArrayOfStrings(final Object[] items) {
    return Arrays.stream(items).map(String::valueOf).toArray(String[]::new);
  }

  public static long countNonNullObjects(Object... objects) {
    return Arrays.stream(objects).filter(Objects::nonNull).count();
  }
}
