FROM node:22-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY server.mjs verify.mjs ./
USER node
EXPOSE 8080
CMD ["node", "server.mjs"]
