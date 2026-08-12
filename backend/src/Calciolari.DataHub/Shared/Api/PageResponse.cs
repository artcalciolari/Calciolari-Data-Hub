namespace Calciolari.DataHub.Shared.Api;

public sealed record PageResponse<T>(
    IReadOnlyList<T> Content,
    int Page,
    int Size,
    long TotalElements,
    int TotalPages)
{
    public static PageResponse<T> Of(IReadOnlyList<T> content, int page, int size, long totalElements)
    {
        var totalPages = size <= 0 ? 0 : (int)Math.Ceiling(totalElements / (double)size);
        return new PageResponse<T>(content, page, size, totalElements, totalPages);
    }
}
