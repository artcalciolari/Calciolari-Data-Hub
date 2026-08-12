using System.Text.Json;
using Calciolari.DataHub.Shared.Api;
using Microsoft.AspNetCore.Diagnostics;

namespace Calciolari.DataHub.Shared.Api;

public static class ExceptionHandling
{
    public static IApplicationBuilder UseDataHubExceptionHandling(this IApplicationBuilder app)
    {
        app.UseExceptionHandler(handler => handler.Run(WriteExceptionProblemAsync));
        app.UseStatusCodePages(context => WriteStatusCodeProblemAsync(context.HttpContext));
        return app;
    }

    internal static async Task WriteExceptionProblemAsync(HttpContext context)
    {
        var feature = context.Features.Get<IExceptionHandlerFeature>();
        var ex = feature?.Error;
        var (status, title, detail, extra) = Describe(ex);
        context.Response.StatusCode = status;
        context.Response.ContentType = "application/problem+json";
        var problem = new Dictionary<string, object?>
        {
            ["type"] = "about:blank",
            ["title"] = title,
            ["status"] = status,
            ["detail"] = Sanitize(detail)
        };
        if (extra is not null)
        {
            foreach (var pair in extra)
            {
                problem[pair.Key] = pair.Value;
            }
        }

        await context.Response.WriteAsync(JsonSerializer.Serialize(problem));
    }

    internal static async Task WriteStatusCodeProblemAsync(HttpContext http)
    {
        if (http.Response.ContentLength is > 0)
        {
            return;
        }

        var status = http.Response.StatusCode;
        if (status < 400)
        {
            return;
        }

        http.Response.ContentType = "application/problem+json";
        var title = status == StatusCodes.Status404NotFound ? "Not Found" : "Error";
        var detail = status == StatusCodes.Status404NotFound ? "Resource not found" : "Request could not be processed";
        var problem = new Dictionary<string, object?>
        {
            ["type"] = "about:blank",
            ["title"] = title,
            ["status"] = status,
            ["detail"] = detail
        };
        await http.Response.WriteAsync(JsonSerializer.Serialize(problem));
    }

    internal static (int Status, string Title, string Detail, Dictionary<string, object?>? Extra) Describe(Exception? ex) =>
        ex switch
        {
            ApiException api => (api.StatusCode, StatusTitle(api.StatusCode), api.Message, null),
            ArgumentException arg => (StatusCodes.Status400BadRequest, "Bad Request", arg.Message, null),
            BadHttpRequestException bad => (StatusCodes.Status400BadRequest, "Bad Request",
                bad.Message.Contains("form", StringComparison.OrdinalIgnoreCase)
                    ? "Missing required part: files"
                    : Sanitize(bad.Message),
                null),
            _ => (StatusCodes.Status500InternalServerError, "Internal Server Error", "Unexpected server error",
                new Dictionary<string, object?> { ["code"] = "UNEXPECTED" })
        };

    internal static string Sanitize(string? message)
    {
        if (string.IsNullOrWhiteSpace(message))
        {
            return "Request could not be processed";
        }

        return message.Length > 500 ? message[..500] : message;
    }

    private static string StatusTitle(int status) => status switch
    {
        StatusCodes.Status400BadRequest => "Bad Request",
        StatusCodes.Status401Unauthorized => "Unauthorized",
        StatusCodes.Status403Forbidden => "Forbidden",
        StatusCodes.Status404NotFound => "Not Found",
        StatusCodes.Status409Conflict => "Conflict",
        StatusCodes.Status413PayloadTooLarge => "Payload Too Large",
        _ => status.ToString()
    };
}
