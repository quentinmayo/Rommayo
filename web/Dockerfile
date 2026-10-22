FROM node:24-alpine
WORKDIR /app
COPY web ./web
COPY shared ./shared
COPY assets/icon.png ./assets/icon.png
COPY desktop/policy.cjs ./desktop/policy.cjs
USER node
ENV HOST=0.0.0.0 PORT=8787
EXPOSE 8787
CMD ["node", "web/server.cjs"]
