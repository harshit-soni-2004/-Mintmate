# Mintmate

> 🚀 **Live Demo:** [https://mintmate-lyart.vercel.app/](https://mintmate-lyart.vercel.app/)

Personal financial intelligence: import transactions, understand spending, and interact with an AI assistant in natural language.

Stack: **Angular** → **Spring Boot REST** → **MySQL**. Modular monolith.

## Local setup

### Database

```bash
docker compose up -d mysql
```

Local defaults (development only): database `spendsense`, user `spendsense`, password `spendsense`. Override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Set `JWT_SECRET` before any non-local use.

### Backend

```bash
cd backend
mvn spring-boot:run
```

API: `http://localhost:8080/api/v1`  
Health: `GET /api/v1/health`

### Frontend

```bash
cd frontend
npm install
npm start
```

UI: `http://localhost:4200`

## Current milestone

Phase 1 foundation: project layout, MySQL, API envelope, validation, global errors, user model, JWT authentication.
