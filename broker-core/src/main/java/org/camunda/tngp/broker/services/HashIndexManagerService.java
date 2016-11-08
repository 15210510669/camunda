package org.camunda.tngp.broker.services;

import static org.camunda.tngp.broker.services.CloseUtil.closeSilently;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.fs.FsLogStorage;
import org.camunda.tngp.log.impl.LogImpl;
import org.camunda.tngp.log.spi.LogStorage;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.agrona.IoUtil;
import org.agrona.LangUtil;

public abstract class HashIndexManagerService<I extends HashIndex<?, ?>> implements Service<HashIndexManager<I>>, HashIndexManager<I>
{
    private final Injector<Log> logInjector = new Injector<>();

    protected final int blockLength;
    protected final int indexSize;

    protected String indexDirPath;
    protected Path indexWorkFilePath;
    protected FileChannelIndexStore indexStore;
    protected RandomAccessFile randomAccessFile;

    protected I index;


    public HashIndexManagerService(int indexSize, int blockLength)
    {
        this.indexSize = indexSize;
        this.blockLength = blockLength;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final Log log = logInjector.getValue();

            final LogStorage logStorage = ((LogImpl)log).getLogContext().getLogStorage();
            final String logPath = ((FsLogStorage) logStorage).getConfig().getPath();

            indexDirPath = logPath + File.separator;
            indexWorkFilePath = new File(String.format("%s%s.idx", indexDirPath, ctx.getName())).toPath();

            try
            {
                Files.deleteIfExists(indexWorkFilePath);

                final Path lastCheckpoint = getLastCheckpoint();
                if (lastCheckpoint != null)
                {
                    Files.copy(lastCheckpoint, indexWorkFilePath);
                }

                randomAccessFile = new RandomAccessFile(indexWorkFilePath.toFile(), "rw");
                indexStore = new FileChannelIndexStore(randomAccessFile.getChannel());
                index = createIndex(indexStore, lastCheckpoint == null);
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        });
    }

    protected abstract I createIndex(FileChannelIndexStore indexStore, boolean createNew);

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.run(() ->
        {
            closeSilently(indexStore);
            closeSilently(randomAccessFile);
            IoUtil.deleteIfExists(indexWorkFilePath.toFile());
        });
    }

    @Override
    public void writeCheckPoint(long logPosition)
    {
        try
        {
            final Path lastCheckpoint = getLastCheckpoint();
            final long lastCheckpointPosition = getLastCheckpointPosition();

            if (lastCheckpointPosition < logPosition)
            {
                final Path newCheckpointPath = new File(String.format("%s.%s", indexWorkFilePath.toString(), logPosition)).toPath();

                // write new checkpoint
                indexStore.flush();
                Files.copy(indexWorkFilePath, newCheckpointPath);

                // delete previous checkpoint
                if (lastCheckpoint != null)
                {
                    Files.deleteIfExists(lastCheckpoint);
                }
            }
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    protected Path getLastCheckpoint()
    {
        final String checkpointPattern = String.format("%s.\\d+", indexWorkFilePath.toFile().getName());
        final String[] checkpoints = new File(indexDirPath).list((d, f) -> f.matches(checkpointPattern));

        if (checkpoints.length > 0)
        {
            return new File(indexDirPath + File.separator + checkpoints[checkpoints.length - 1]).toPath().toAbsolutePath();
        }
        else
        {
            return null;
        }

    }

    @Override
    public long getLastCheckpointPosition()
    {
        final Path lastCheckpoint = getLastCheckpoint();

        long lastCheckpointPosition = -1;

        if (lastCheckpoint != null)
        {
            final String pathString = lastCheckpoint.toString();
            lastCheckpointPosition = Long.parseLong(pathString.substring(pathString.lastIndexOf(".") + 1));
        }

        return lastCheckpointPosition;
    }

    @Override
    public HashIndexManagerService<I> get()
    {
        return this;
    }

    public I getIndex()
    {
        return index;
    }

    public Injector<Log> getLogInjector()
    {
        return logInjector;
    }

}
