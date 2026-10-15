# PropBuy — Backend API

This is the Spring Boot 3.3 / Java 17 REST API backend for **PropBuy** (property acquisition and resale platform).

## Tech Stack
- Java 17
- Spring Boot 3.3 (Web, Data JPA, Security, Actuator, Mail, Validation)
- MySQL 8.x with Flyway migrations (or in-memory H2 in demo mode)
- JWT Authentication
- Dockerfile & Render Blueprint

## Deployment (Render)
1. In Render, select **New +** > **Web Service**.
2. Connect this repository (`PropBuy-backend`).
3. Settings:
   - **Language / Runtime**: `Docker`
   - **Plan**: `Free`
   - **Health Check Path**: `/api/health`
   - **Environment Variables**:
     - `SPRING_PROFILES_ACTIVE`: `demo`
     - `APP_JWT_SECRET`: `any-random-32-characters-secret`
     - `CORS_ORIGINS`: `https://*.vercel.app,http://localhost:8080,http://localhost:5500`
     - `PORT`: `8080`
4. Click **Create Web Service**.
