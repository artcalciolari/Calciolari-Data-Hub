namespace Calciolari.DataHub.Imports.Application;

public interface IImportWorkQueue
{
    void Enqueue(Guid importFileId);

    IAsyncEnumerable<Guid> ReadAllAsync(CancellationToken cancellationToken);
}

public sealed class ImportWorkQueue : IImportWorkQueue
{
    private readonly System.Threading.Channels.Channel<Guid> _channel =
        System.Threading.Channels.Channel.CreateUnbounded<Guid>(
            new System.Threading.Channels.UnboundedChannelOptions
            {
                SingleReader = true,
                SingleWriter = false
            });

    public void Enqueue(Guid importFileId)
    {
        if (!_channel.Writer.TryWrite(importFileId))
        {
            throw new InvalidOperationException("import work queue is complete");
        }
    }

    public IAsyncEnumerable<Guid> ReadAllAsync(CancellationToken cancellationToken) =>
        _channel.Reader.ReadAllAsync(cancellationToken);

    internal bool TryDequeue(out Guid importFileId) => _channel.Reader.TryRead(out importFileId);

    internal void Complete() => _channel.Writer.Complete();
}
