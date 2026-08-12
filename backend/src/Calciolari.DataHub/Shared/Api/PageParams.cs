namespace Calciolari.DataHub.Shared.Api;

public static class PageParams
{
    public const int DefaultSize = 20;
    public const int MaxSize = 100;

    public static int Page(int? page)
    {
        var value = page ?? 0;
        if (value < 0)
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "page must be >= 0");
        }

        return value;
    }

    public static int Size(int? size)
    {
        var value = size ?? DefaultSize;
        if (value < 1 || value > MaxSize)
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "size must be between 1 and " + MaxSize);
        }

        return value;
    }
}
