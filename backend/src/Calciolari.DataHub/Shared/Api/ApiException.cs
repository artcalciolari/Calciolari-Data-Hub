namespace Calciolari.DataHub.Shared.Api;

public sealed class ApiException : Exception
{
    public int StatusCode { get; }

    public ApiException(int statusCode, string message)
        : base(message)
    {
        StatusCode = statusCode;
    }

    public ApiException(int statusCode, string message, Exception inner)
        : base(message, inner)
    {
        StatusCode = statusCode;
    }
}
