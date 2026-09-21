# Scan ID

Spring Boot web app that scans a college ID barcode, extracts the roll number, and retrieves student details from the college portal.

## Run locally

```powershell
$env:PATH = "C:\Tools\maven\apache-maven-3.9.9\bin;$env:PATH"
mvn clean package -DskipTests
java -jar target/scanid-app-0.0.1-SNAPSHOT.jar
```

Open http://localhost:8080.

## Deploy to Render

This repository includes a `Dockerfile` and `render.yaml`. Create a Render Blueprint from the GitHub repository, or create a Docker web service with these settings:

- Plan: Free
- Health check path: `/`
- Render supplies the `PORT` environment variable automatically.

The browser camera requires the deployed HTTPS URL and one-time camera permission.
