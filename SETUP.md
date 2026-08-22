# Chronos Setup Guide

This guide explains how to install Chronos and run both parts of the application locally. It is written for someone who is setting up the project for the first time on Windows.

## What You Are Running

Chronos has two applications:

- **Backend**: a Java Spring Boot API that runs on port `8080`.
- **Frontend**: a React application served by Vite that runs on port `5173`.

The frontend sends API requests through Vite's proxy. A request to `/api/welcome` from the browser is forwarded to the backend at `http://localhost:8080/api/welcome`.

## Prerequisites

Install these tools before continuing. Close and reopen VS Code after installing them so its terminals can find the new commands.

| Tool | Required version | Download |
| --- | --- | --- |
| Git | Any current version | [git-scm.com/download/win](https://git-scm.com/download/win) |
| Java JDK | 25 | [Microsoft Build of OpenJDK](https://learn.microsoft.com/en-us/java/openjdk/download) |
| Apache Maven | 3.9 or newer | [maven.apache.org/download.cgi](https://maven.apache.org/download.cgi) |
| Node.js | 20 or newer | [nodejs.org/en/download](https://nodejs.org/en/download) |
| Visual Studio Code | Current version | [code.visualstudio.com/download](https://code.visualstudio.com/download) |

Node.js includes npm. You do not normally need to install npm separately.

### Check Your Installation

Open a new PowerShell terminal and run:

```powershell
java -version
mvn -version
node --version
npm --version
git --version
```

Confirm that Java reports version 25, Maven reports 3.9 or newer, and Node.js reports 20 or newer. If a command is not recognized, reinstall that tool or restart VS Code after installation.

## Get The Project

If you received the project as a ZIP file, extract it first. If you are cloning it from a Git repository, run:

```powershell
git clone <repository-url>
cd Chronos
```

In VS Code, choose **File > Open Folder** and open the `Chronos` folder. The folder you open should contain `backend`, `frontend`, and `SETUP.md`.

## Project Structure

```text
Chronos/
  backend/
    pom.xml                         Maven and Spring Boot configuration
    src/main/java/                  Backend Java source code
    src/main/resources/             Backend configuration
    src/test/java/                  Backend tests
  frontend/
    package.json                    Frontend scripts and dependencies
    src/                            React source code
    vite.config.js                  Frontend dev server and API proxy
  SETUP.md                          This guide
```

## First-Time Setup

### 1. Install Frontend Dependencies

From the project root, run:

```powershell
cd frontend
npm install
```

This reads `frontend/package.json` and creates the `node_modules` folder. You normally only need to run it once, or again after `package.json` changes.

If Windows displays an npm error about a missing or undefined file argument, run this in the same PowerShell terminal and retry:

```powershell
$env:ComSpec='C:\Windows\System32\cmd.exe'
npm install
```

### 2. Download Backend Dependencies

Maven downloads the backend dependencies automatically. You can download them and compile the project with:

```powershell
cd ..\backend
mvn test-compile
```

The first Maven run can take longer because it downloads dependencies into your local Maven cache.

## Start The Application

You need two terminals running at the same time. In VS Code, use **Terminal > New Terminal** twice.

### Terminal 1: Start The Backend

From the project root:

```powershell
cd backend
mvn spring-boot:run
```

Wait until the terminal contains a message similar to `Tomcat started on port 8080`. Keep this terminal open while using the application.

The backend is available at:

- Application: [http://localhost:8080](http://localhost:8080)
- Welcome API: [http://localhost:8080/api/welcome](http://localhost:8080/api/welcome)

The API should return:

```json
{"message":"Welcome to Chronos website - Maxwells time keeping app"}
```

### Terminal 2: Start The Frontend

Open a second terminal, return to the project root, and run:

```powershell
cd frontend
npm run dev
```

Open the URL printed by Vite, normally [http://localhost:5173](http://localhost:5173). If `npm run dev` exits immediately on Windows, use:

```powershell
npm.cmd run dev
```

Keep this terminal open too. Vite automatically refreshes the browser when frontend files change.

## Verify Everything Works

1. Open [http://localhost:5173](http://localhost:5173) in a browser.
2. Confirm the page loads.
3. Open [http://localhost:8080/api/welcome](http://localhost:8080/api/welcome) and confirm JSON is returned.
4. From the frontend URL, open [http://localhost:5173/api/welcome](http://localhost:5173/api/welcome) to verify the Vite proxy reaches the backend.

You can also run the automated checks from separate terminals:

```powershell
cd backend
mvn clean test
```

```powershell
cd frontend
npm run build
```

Both commands should finish successfully.

## Stop The Application

In each terminal running a server, press `Ctrl+C`. Stop both the backend and frontend when you are finished.

## Troubleshooting

### Port 8080 Is Already In Use

Another application is using the backend port. Find the process with:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

Close that application and start the backend again. If you change the backend port, update the frontend proxy in `frontend/vite.config.js` as well.

### Port 5173 Is Already In Use

Vite may choose another port and print it in the terminal. Open the printed URL. If you need to find the process using port 5173, run:

```powershell
Get-NetTCPConnection -LocalPort 5173 -State Listen
```

### The Frontend Says The API Is Unavailable

Make sure the backend terminal is still running and that [http://localhost:8080/api/welcome](http://localhost:8080/api/welcome) returns JSON. Start the backend before refreshing the frontend.

### Java Version Errors

The backend is configured for Java 25. Check the active Java installation with:

```powershell
java -version
mvn -version
```

Both commands should point to a JDK 25 installation. Restart VS Code after changing `JAVA_HOME` or your system PATH.

## Additional Documentation

- [Spring Boot documentation](https://docs.spring.io/spring-boot/index.html)
- [Maven getting started guide](https://maven.apache.org/guides/getting-started/)
- [React documentation](https://react.dev/learn)
- [Vite documentation](https://vite.dev/guide/)
- [npm documentation](https://docs.npmjs.com/)

Database setup is not required yet. Persistence can be added later when database-backed features are introduced.
