FROM node:24-bookworm-slim
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY src ./src
COPY migrations ./migrations
COPY scripts ./scripts
RUN mkdir -p /var/lib/rtc/media && chown -R node:node /var/lib/rtc/media
ENV NODE_ENV=production PORT=3000 MEDIA_DIR=/var/lib/rtc/media
USER node
EXPOSE 3000
CMD ["node", "src/index.js"]
