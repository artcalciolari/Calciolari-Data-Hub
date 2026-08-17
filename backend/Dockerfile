FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build

WORKDIR /src
COPY global.json ./
COPY src/Calciolari.DataHub/Calciolari.DataHub.csproj src/Calciolari.DataHub/
RUN dotnet restore src/Calciolari.DataHub/Calciolari.DataHub.csproj

COPY src/Calciolari.DataHub/ src/Calciolari.DataHub/
RUN dotnet publish src/Calciolari.DataHub/Calciolari.DataHub.csproj \
    --configuration Release \
    --output /app/publish \
    --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime

# The ASP.NET runtime image does not guarantee an HTTP client. curl is used
# only by the container healthcheck below.
USER root
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/publish .
RUN mkdir -p /app/data/raw-storage \
    && chown -R "$APP_UID":"$APP_UID" /app

ENV ASPNETCORE_URLS=http://0.0.0.0:8080 \
    DATAHUB_RAW_STORAGE_ROOT=/app/data/raw-storage

EXPOSE 8080
USER $APP_UID
ENTRYPOINT ["dotnet", "Calciolari.DataHub.dll"]
