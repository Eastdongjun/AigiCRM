FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
# 【2026-10-16 文件阅读器】SheetJS CE 不走 npm 源(注册表版本陈旧含漏洞), 以 vendor/ 内 tarball 的 file: 依赖锁版;
# 必须先于 npm ci 拷入, 否则锁文件里的 file:vendor/... 解析不到。远程 URL 依赖在 npm ci 下会触发
# "Exit handler never called"(测试服构建两次实证), 故不用 URL 形式。
COPY vendor ./vendor
# 【2026-10-16 构建可靠性】国内服务器容器内直连 registry.npmjs.org 不稳(测试服三次 npm ci 失败:
# 一次裸超时、两次 "Exit handler never called" 均为下载中断的表象; 切 npmmirror 后 7s 装完 266 包)。
# 锁文件的 resolved 指向 npmjs, npm 默认 replace-registry-host 会改走镜像; 每个包仍按锁文件 integrity(sha512)校验,
# 供应链完整性不因换源下降。
RUN npm config set registry https://registry.npmmirror.com
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
