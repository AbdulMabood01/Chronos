# Chronos - Employee Timekeeping & Vacation Management System

![Status](https://img.shields.io/badge/status-production%20ready-brightgreen)
![Java](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-blue)
![React](https://img.shields.io/badge/React-18.3.1-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-blue)

A modern, full-stack employee timekeeping and vacation management application designed for Maxwell Network (150 employees). Built with Spring Boot 3.5.14, React 18.3.1, PostgreSQL, and OAuth2 authentication via Microsoft Entra ID.

## Features

### Employee Features
- ✅ **Timesheet Management**
  - Monthly timesheets with daily time entry tracking
  - Submit timesheets for manager approval
  - Track approval status and rejection reasons
  - View historical timesheets

- ✅ **Vacation Management**
  - Request vacation/sick/personal days
  - Automatic working day calculation
  - Submit requests for approval
  - View request history and status

- ✅ **Notifications**
  - In-app notifications for approvals/rejections
  - Mark notifications as read
  - Notification center dashboard

- ✅ **Dashboard**
  - Quick access to pending timesheets and vacation requests
  - Unread notification count
  - Recent activity overview

### Manager/Super Admin Features
- ✅ **Approval Workflows**
  - Review pending timesheets with employee details
  - Approve/reject timesheet submissions
  - Provide rejection reasons for resubmission
  - Review and approve/reject vacation requests

- ✅ **Employee Management**
  - View all employees and their status
  - Update hourly rates for payroll
  - Deactivate/reactivate employee accounts
  - Change employee roles

- ✅ **Audit & Compliance**
  - Complete audit trail of all actions
  - Track user changes, approvals, rejections
  - JSONB-based audit details for flexibility
  - Immutable audit logs for compliance

- ✅ **Reporting**
  - View all pending approvals
  - Employee activity tracking
  - Historical data access

## Technology Stack

### Backend
- **Framework**: Spring Boot 3.5.14
- **Language**: Java 25
- **Database**: PostgreSQL 14+ (with Flyway migrations)
- **ORM**: Hibernate/Spring Data JPA
- **Security**: Spring Security 6.x with OAuth2/JWT
- **Authentication**: Microsoft Entra ID (OAuth2)
- **Build**: Maven 3.9+
- **Testing**: JUnit 5, Mockito, TestContainers

### Frontend
- **Library**: React 18.3.1
- **Build Tool**: Vite 5.4.10
- **Routing**: React Router v6
- **HTTP Client**: Axios
- **State Management**: React Context API
- **Date Handling**: date-fns
- **Export**: XLSX library
- **Styling**: CSS (TailwindCSS ready)

### Infrastructure
- **API**: RESTful with JWT authentication
- **CORS**: Configured for development and production
- **Deployment**: Docker-ready
- **CI/CD**: Ready for GitHub Actions

## Quick Start

### Prerequisites
- Java JDK 25+
- Maven 3.9+
- Node.js 20+
- PostgreSQL 14+
- Git

### Installation & Setup

```bash
# Clone repository
git clone https://github.com/maxwell-network/chronos.git
cd chronos

# Backend setup
cd backend
mvn clean install

# Frontend setup
cd ../frontend
npm install

# Database setup (PostgreSQL)
psql -U postgres
CREATE DATABASE chronos_dev;
CREATE USER chronos_user WITH PASSWORD 'chronos_password';
GRANT ALL PRIVILEGES ON DATABASE chronos_dev TO chronos_user;
\q
```

### Running Development Servers

**Terminal 1 - Backend (Port 8080)**:
```bash
cd backend
mvn spring-boot:run
```

**Terminal 2 - Frontend (Port 5173)**:
```bash
cd frontend
npm run dev
```

Open [http://localhost:5173](http://localhost:5173) in your browser.

### API Testing

```bash
# Welcome endpoint
curl http://localhost:8080/api/welcome

# Get authenticated user (requires JWT token)
curl -H "Authorization: Bearer <your-jwt-token>" \
     http://localhost:8080/api/auth/me
```

## Project Structure

```
chronos/
├── backend/
│   ├── src/main/
│   │   ├── java/com/maxwell/chronos/
│   │   │   ├── domain/              # JPA entities
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   ├── repository/          # Spring Data repositories
│   │   │   ├── service/             # Business logic
│   │   │   ├── web/                 # REST controllers
│   │   │   ├── enums/               # Status/role enums
│   │   │   ├── config/              # Spring configuration
│   │   │   └── ChronosApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/        # Flyway SQL migrations
│   ├── pom.xml
│   └── README.md
├── frontend/
│   ├── src/
│   │   ├── pages/                   # Page components
│   │   ├── AuthContext.jsx          # Authentication state
│   │   ├── api.js                   # API client
│   │   ├── App.jsx
│   │   └── styles.css
│   ├── package.json
│   ├── vite.config.js
│   └── index.html
├── SETUP.md                         # Initial setup guide
├── DEVELOPMENT.md                   # Development guide
├── architecture.md                  # Architecture decisions
└── README.md                        # This file
```

## API Endpoints

### Authentication
- `POST /auth/login` - OAuth2 login
- `GET /auth/me` - Get current user

### Users (Admin Only)
- `GET /users` - List active users
- `GET /users/all` - List all users
- `GET /users/{id}` - Get user details
- `PATCH /users/{id}/hourly-rate` - Update hourly rate
- `PATCH /users/{id}/deactivate` - Deactivate user
- `PATCH /users/{id}/reactivate` - Reactivate user

### Timesheets
- `GET /timesheets/{year}/{month}` - Get/create timesheet
- `POST /timesheets/{id}/time-entries` - Add time entry
- `POST /timesheets/{id}/submit` - Submit for approval
- `GET /timesheets/pending` - Get pending timesheets (admin)
- `GET /timesheets/my` - Get user's timesheets

### Vacation
- `POST /vacation` - Create vacation request
- `POST /vacation/{id}/submit` - Submit for approval
- `GET /vacation/pending` - Get pending requests (admin)
- `GET /vacation/my` - Get user's requests

### Approvals
- `POST /approvals/timesheet/{id}/approve` - Approve timesheet (admin)
- `POST /approvals/timesheet/{id}/reject` - Reject timesheet (admin)
- `POST /approvals/vacation/{id}/approve` - Approve vacation (admin)
- `POST /approvals/vacation/{id}/reject` - Reject vacation (admin)

### Notifications
- `GET /notifications` - Get notifications
- `GET /notifications/unread` - Get unread notifications
- `GET /notifications/unread-count` - Get unread count
- `PATCH /notifications/{id}/read` - Mark as read
- `POST /notifications/read-all` - Mark all as read

### Audit (Admin Only)
- `GET /audit` - Get audit logs

## Database Schema

### Core Entities
- **User** - Employee records with roles and status
- **Timesheet** - Monthly timesheets with status workflow
- **TimeEntry** - Daily time entries within timesheets
- **VacationRequest** - Vacation/sick day requests
- **VacationType** - Lookup table for vacation types
- **Notification** - In-app notifications
- **AuditLog** - Immutable audit trail

### Status Workflows
Both Timesheet and VacationRequest follow workflow:
`DRAFT → SUBMITTED → APPROVED/REJECTED → LOCKED`

## Authentication & Security

- **OAuth2 with JWT**: Microsoft Entra ID integration
- **Server-side Authorization**: Every endpoint validates user roles
- **HTTPS Ready**: Configured for production TLS
- **CORS Protection**: Configured for specified origins
- **Audit Logging**: All actions logged immutably

## Development

### Run Tests
```bash
# Backend
cd backend
mvn test

# Frontend
cd frontend
npm test
```

### Build for Production
```bash
# Backend (creates JAR)
cd backend
mvn clean package -DskipTests

# Frontend (creates dist folder)
cd frontend
npm run build
```

### Docker Build
```bash
docker build -t chronos:1.0.0 backend/
docker run -p 8080:8080 -e DATABASE_URL=... chronos:1.0.0
```

## Configuration

### Development Environment Variables

**Backend** (`.env`):
```env
DATABASE_URL=jdbc:postgresql://localhost:5432/chronos_dev
DATABASE_USERNAME=chronos_user
DATABASE_PASSWORD=chronos_password
```

**Frontend** (`.env.local`):
```env
VITE_API_URL=http://localhost:8080/api
```

See `.env.example` files for complete configuration options.

## Roadmap

### Phase 1 (Completed)
- [x] Backend REST API
- [x] Frontend structure
- [x] Authentication layer
- [x] Core workflows

### Phase 2 (In Progress)
- [ ] Enhanced UI/UX
- [ ] Email notifications
- [ ] Advanced reporting
- [ ] Excel export

### Phase 3 (Planned)
- [ ] Mobile app support
- [ ] Analytics dashboard
- [ ] Performance optimizations
- [ ] Multi-language support

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/my-feature`
5. Submit pull request

### Code Standards
- Follow Google Java Style Guide (backend)
- Use ESLint + Prettier (frontend)
- Write tests for new features
- Update documentation

## Troubleshooting

See [DEVELOPMENT.md](DEVELOPMENT.md#troubleshooting) for detailed troubleshooting guide covering:
- Backend startup issues
- Database connection problems
- Frontend build errors
- Authentication issues

## Support

For issues, questions, or suggestions:
1. Check existing GitHub issues
2. Review documentation files
3. Create detailed issue report
4. Contact development team

## License

MIT License - See LICENSE file for details

## Acknowledgments

- Maxwell Network for product requirements
- Spring Boot and React communities
- PostgreSQL documentation
- Microsoft Entra ID documentation

---

**Version**: 1.0.0  
**Last Updated**: 2024  
**Status**: Production Ready

For detailed setup instructions, see [SETUP.md](SETUP.md)  
For development guide, see [DEVELOPMENT.md](DEVELOPMENT.md)  
For architecture details, see [architecture.md](architecture.md)
