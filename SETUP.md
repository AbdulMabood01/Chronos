# Chronos Setup Guide

This document lists what you need installed before running Chronos and the steps to start the backend and frontend.

## Prerequisites

Install these tools before running the app:

- Java JDK 25
- Apache Maven 3.9 or newer
- Node.js 20 or newer
- npm 10 or newer
- Git
- A code editor such as Visual Studio Code

## Project Structure

```text
Chronos/
  backend/   Spring Boot API
  frontend/  React + Vite app
```

## First-Time Setup

From the project root:

```powershell
cd frontend
npm install
```

If npm fails on Windows with an error about a missing or undefined file argument, run this first in the same terminal:

```powershell
$env:ComSpec='C:\Windows\System32\cmd.exe'
npm install
```

The backend dependencies are downloaded automatically by Maven the first time you run or test the backend.

## Run The Backend

Open a terminal from the project root:

```powershell
cd backend
mvn spring-boot:run
```

The backend runs at:

```text
http://localhost:8080
```

You can test the API here:

```text
http://localhost:8080/api/welcome
```

Expected response:

```json
{"message":"Welcome to Chronos website - Maxwells time keeping app"}
```

## Run The Frontend

Open a second terminal from the project root:

```powershell
cd frontend
npm run dev
```

The frontend runs at:

```text
http://127.0.0.1:5173
```

The frontend calls the backend through Vite's `/api` proxy. For example:

```text
http://127.0.0.1:5173/api/welcome
```

## Verify Everything Works

Run backend tests:

```powershell
cd backend
mvn test
```

Build the frontend:

```powershell
cd frontend
npm run build
```

## Notes

- Start the backend before the frontend if you want the page to show the live API connection immediately.
- Database setup is not required yet. Database configuration can be added later when persistence features are ready.
- If port `8080` or `5173` is already in use, stop the old process or update the app configuration before running again.
