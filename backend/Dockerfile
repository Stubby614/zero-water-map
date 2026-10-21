FROM oven/bun:1-alpine

WORKDIR /app

# Copy open-data seed & backend source
COPY open-data ./open-data
COPY backend/package.json ./backend/package.json
COPY backend/src ./backend/src

WORKDIR /app/backend

ENV NODE_ENV=production
ENV PORT=8080
ENV OPEN_DATA_PATH=/app/open-data/stations.json

EXPOSE 8080

CMD ["bun", "run", "src/server.ts"]
