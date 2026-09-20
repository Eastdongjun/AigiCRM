FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
# 【PR-23.D-03 锁文件冻结安装】npm ci 严格按 package-lock.json 安装(失同步构建即失败), 杜绝语义漂移
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
# 模板目录：官方 nginx 镜像的 entrypoint 会在启动时 envsubst 生成 conf.d/default.conf
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
