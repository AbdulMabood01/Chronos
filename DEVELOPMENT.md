# Chronos - Complete Setup & Development Guide

## Overview

Chronos is a production-ready employee timekeeping and vacation management system for Maxwell Network (150 employees). It consists of:

- **Backend**: Spring Boot 3.5.14 REST API (Java 25, PostgreSQL)
- **Frontend**: React 18.3.1 with Vite (TypeScript-ready)
- **Database**: PostgreSQL with Flyway migrations
- **Authentication**: OAuth2 with Microsoft Entra ID
- **Deployment**: Container-ready (Docker support planned)

## System Requirements

### Development Environment

| Component | Version | Purpose |
|-----------|---------|---------|
| Java JDK | 25+ | Spring Boot backend |
| Maven | 3.9+ | Build & dependency management |
| Node.js | 20+ | Frontend tooling |
| PostgreSQL | 14+ | Database |
| Git | 2.30+ | Version control |
| VS Code | Latest | IDE (optional but recommended) |

### Installation Instructions

```powershell
# Check versions
java -version
mvn -version
node --version
npm --version
psql --version

# Install PostgreSQL on Windows:
# 1. Download from https://www.postgresql.org/download/windows/
# 2. Run installer, note the password for postgres user
# 3. Ensure PostgreSQL service is running
```

## Project Structure

```
Chronos/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/maxwell/chronos/
│   │   │   │   ├── ChronosApplication.java
│   │   │   │   ├── config/           # Spring Security, CORS config
│   │   │   │   ├── domain/           # Entity classes
│   │   │   │   ├── dto/              # Data Transfer Objects
│   │   │   │   ├── enums/            # Status enums
│   │   │   │   ├── repository/       # JPA repositories
│   │   │   │   ├── service/          # Business logic
│   │   │   │   └── web/              # REST controllers
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/     # Flyway SQL migrations
│   │   └── test/                     # Test classes
│   └── pom.xml                       # Maven configuration
├── frontend/
│   ├── src/
│   │   ├── pages/                    # Page components
│   │   ├── AuthContext.jsx           # Auth state management
│   │   ├── api.js                    # API client
│   │   ├── App.jsx                   # Main app component
│   │   ├── main.jsx                  # Entry point
│   │   └── styles.css                # Global styles
│   ├── package.json                  # Node dependencies
│   ├── vite.config.js                # Vite configuration
│   └── index.html                    # HTML template
├── .env.example                      # Backend env template
├── SETUP.md                          # Original setup guide
├── architecture.md                   # Architecture documentation
└── DEVELOPMENT.md                    # THIS FILE
```

## Database Setup

### Step 1: Create Database & User

Open PostgreSQL terminal:

```powershell
psql -U postgres
```

Execute in PostgreSQL:

```sql
-- Create database
CREATE DATABASE chronos_dev ENCODING 'UTF8';

-- Create user
CREATE USER chronos_user WITH PASSWORD 'chronos_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE chronos_dev TO chronos_user;

-- Connect to the database
\c chronos_dev

-- Grant schema privileges
GRANT ALL ON SCHEMA public TO chronos_user;

-- Verify
\du
\l
```

Exit PostgreSQL:

```
\q
```

### Step 2: Initialize Schema (Automatic)

When the Spring Boot backend starts, Flyway automatically:
1. Creates all tables and enums
2. Inserts default vacation types
3. Sets up indexes and constraints

No manual SQL execution needed.

## Configuration

### Backend Configuration

**File**: `backend/src/main/resources/application.properties`

Key properties (pre-configured for development):

```properties
# Server
server.port=8080
server.servlet.context-path=/api

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/chronos_dev
spring.datasource.username=chronos_user
spring.datasource.password=chronos_password

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# OAuth2 (Entra ID)
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/common
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.microsoftonline.com/common/discovery/v2.0/keys

# CORS
cors.allowed-origins=http://localhost:5173,http://localhost:3000
```

**For Production**, update:
- Database credentials (use `.env` file)
- CORS origins (your domain)
- OAuth2 tenant ID (your Azure directory)

### Frontend Configuration

**File**: `frontend/.env.local`

```env
VITE_API_URL=http://localhost:8080/api
VITE_APP_NAME=Chronos
VITE_APP_VERSION=1.0.0
```

The Vite dev server proxies `/api` requests to the backend (see `vite.config.js`).

## Development Workflow

### First-Time Setup

```powershell
# 1. Clone repository
git clone <repo-url>
cd Chronos

# 2. Install frontend dependencies
cd frontend
npm install

# 3. Download backend dependencies
cd ../backend
mvn clean dependency:resolve

# 4. Ensure PostgreSQL is running and database exists
# Verify database connection:
psql -U chronos_user -d chronos_dev -c "SELECT version();"
```

### Running Development Servers

**Terminal 1 - Backend**:

```powershell
cd Chronos/backend
mvn spring-boot:run
```

Expected output:
```
Tomcat started on port(s): 8080 (http)
```

Test: `curl http://localhost:8080/api/welcome`

**Terminal 2 - Frontend**:

```powershell
cd Chronos/frontend
npm run dev
```

Expected output:
```
VITE v5.4.10 ready in 234 ms
➜  Local:   http://localhost:5173/
```

Open [http://localhost:5173](http://localhost:5173) in browser.

### Development Best Practices

1. **Commit Strategy**
   ```powershell
   git add .
   git commit -m "feature: add vacation approval email notifications"
   git push origin feature/email-notifications
   ```

2. **Backend Testing**
   ```powershell
   cd backend
   mvn test                    # Run all tests
   mvn test -Dtest=TimeEntry* # Run specific test
   ```

3. **Frontend Development**
   - Vite hot-reloads on `.jsx` and `.css` changes
   - Browser DevTools: F12 for React Devtools

4. **API Testing**
   ```powershell
   # Test API endpoints
   curl -H "Authorization: Bearer <token>" \
        http://localhost:8080/api/users

   # Or use Postman/Insomnia
   ```

## API Endpoints

### Authentication

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/auth/login` | Entra ID OAuth login |
| GET | `/auth/me` | Get current user profile |

### Users (Admin Only)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/users/{id}` | Get user by ID |
| GET | `/users` | List active users |
| GET | `/users/all` | List all users (admin) |
| PATCH | `/users/{id}/hourly-rate?rate=X` | Update hourly rate |
| PATCH | `/users/{id}/deactivate` | Deactivate user |
| PATCH | `/users/{id}/reactivate` | Reactivate user |

### Timesheets

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/timesheets/{year}/{month}` | Get/create timesheet |
| POST | `/timesheets/{id}/time-entries` | Add time entry |
| POST | `/timesheets/{id}/submit` | Submit for approval |
| GET | `/timesheets/pending` | List pending (admin) |
| GET | `/timesheets/my` | List user's timesheets |

### Vacation

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/vacation` | Create request |
| POST | `/vacation/{id}/submit` | Submit for approval |
| GET | `/vacation/pending` | List pending (admin) |
| GET | `/vacation/my` | List user's requests |

### Approvals

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/approvals/timesheet/{id}/approve` | Approve timesheet |
| POST | `/approvals/timesheet/{id}/reject?reason=X` | Reject timesheet |
| POST | `/approvals/vacation/{id}/approve` | Approve vacation |
| POST | `/approvals/vacation/{id}/reject?reason=X` | Reject vacation |

### Notifications

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/notifications` | Get all notifications |
| GET | `/notifications/unread` | Get unread only |
| GET | `/notifications/unread-count` | Count unread |
| PATCH | `/notifications/{id}/read` | Mark as read |
| POST | `/notifications/read-all` | Mark all as read |

### Audit

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/audit` | Get audit logs (admin) |

## Authentication Flow (Development)

1. **Frontend Login**: User provides OAuth token
2. **Backend Validation**: Verifies token signature against Entra ID public keys
3. **User Creation**: Automatic creation on first login
4. **Session**: JWT token stored in localStorage
5. **All Requests**: Include `Authorization: Bearer <token>` header

For local development without Entra ID:
- Generate a test JWT token
- Manually set in localStorage
- Backend validates against configured issuer

## Testing

### Backend Tests

```powershell
cd backend

# Run all tests
mvn test

# Run with coverage
mvn clean test jacoco:report

# Run specific test class
mvn test -Dtest=TimesheetServiceTest

# Run specific test method
mvn test -Dtest=TimesheetServiceTest#testSubmitTimesheet
```

Test Database: Uses TestContainers with PostgreSQL for integration tests.

### Frontend Tests

```powershell
cd frontend

# Run unit tests
npm run test

# Run with coverage
npm run test:coverage

# Run in watch mode
npm run test:watch
```

## Troubleshooting

### Backend Won't Start

```powershell
# Check port 8080 is available
netstat -ano | findstr :8080

# Kill process on port 8080
Stop-Process -Id <PID> -Force

# Verify PostgreSQL is running
psql -U chronos_user -d chronos_dev -c "SELECT 1"

# Check logs for errors
mvn spring-boot:run | Select-String "ERROR|Exception"
```

### Frontend Won't Load

```powershell
# Clear node_modules and reinstall
rm -r frontend/node_modules
npm install

# Clear Vite cache
rm -r frontend/.vite

# Check port 5173 is available
netstat -ano | findstr :5173
```

### Database Connection Issues

```powershell
# Test connection
psql -U chronos_user -d chronos_dev -c "SELECT version();"

# Reset database (WARNING: deletes all data)
psql -U postgres -c "DROP DATABASE IF EXISTS chronos_dev;"
psql -U postgres -c "CREATE DATABASE chronos_dev ENCODING 'UTF8';"
psql -U postgres -d chronos_dev -c "GRANT ALL PRIVILEGES ON DATABASE chronos_dev TO chronos_user;"

# Restart PostgreSQL service
Restart-Service -Name PostgreSQL*
```

## Build for Production

### Backend Build

```powershell
cd backend
mvn clean package -DskipTests

# Result: backend/target/chronos-1.0.0.jar
```

### Frontend Build

```powershell
cd frontend
npm run build

# Result: frontend/dist/ (ready for web server)
```

## Docker (Optional)

Create `backend/Dockerfile`:

```dockerfile
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app
COPY target/chronos-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build and run:

```bash
docker build -t chronos:1.0.0 .
docker run -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/chronos_dev \
           -e SPRING_DATASOURCE_USERNAME=chronos_user \
           -e SPRING_DATASOURCE_PASSWORD=chronos_password \
           -p 8080:8080 chronos:1.0.0
```

## Next Steps

1. **Complete Frontend Implementation**
   - [ ] Timesheet calendar interface
   - [ ] Vacation request form
   - [ ] Admin approval dashboard
   - [ ] Employee management UI
   - [ ] Reporting/analytics

2. **Backend Enhancements**
   - [ ] Excel export for timesheets
   - [ ] Email notifications
   - [ ] Dashboard statistics endpoints
   - [ ] Advanced filtering & search
   - [ ] Comprehensive API documentation

3. **Testing & Quality**
   - [ ] Unit test coverage (target: 80%+)
   - [ ] Integration tests for workflows
   - [ ] End-to-end tests with Playwright
   - [ ] Performance testing & optimization

4. **Security & Deployment**
   - [ ] Production Entra ID configuration
   - [ ] SSL/TLS certificate setup
   - [ ] Security headers & HTTPS
   - [ ] Azure App Service deployment
   - [ ] CI/CD pipeline (GitHub Actions)

5. **Documentation**
   - [ ] API OpenAPI/Swagger documentation
   - [ ] User guide
   - [ ] Admin configuration guide
   - [ ] Architecture decision records

## Support & Contributions

For issues or questions:
1. Check troubleshooting section above
2. Review existing issues on GitHub
3. Create detailed bug report with:
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details
   - Error logs/stack traces

For contributions:
1. Create feature branch: `git checkout -b feature/your-feature`
2. Follow existing code style
3. Add tests for new functionality
4. Submit pull request with description

## License & Contact

**Project**: Chronos v1.0.0  
**Organization**: Maxwell Network  
**Employees**: 150  
**Contact**: Development Team

---

Last Updated: $(date)  
Revision: 1.0
