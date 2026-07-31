# ============================================================
# 心屿前端镜像 (M1-7): 多阶段构建
#   stage 1  node 编译 Vue3 产物 (npm ci 保证与 lock 文件一致)
#   stage 2  nginx 托管静态资源 + 反向代理 /api (配置见 nginx/nginx.conf)
# ============================================================

FROM node:22-alpine AS build
WORKDIR /build
# 先只拷依赖清单, 利用层缓存: 改源码不重装 node_modules
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# compose 会把仓库根 nginx/nginx.conf 挂载覆盖默认站点配置
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
