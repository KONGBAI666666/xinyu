# ============================================================
# 心屿 XinYu · 一键启动脚本 (本地开发)
# 用法: 右键"使用 PowerShell 运行", 或在终端执行 .\start.ps1
# 依次启动: Qdrant(7333) → 后端(9200) → 前端(5280), 并自动打开浏览器
#
# 可靠性说明:
# - 端口探测用真实 TCP 连接, 不用 netstat 文本匹配 (避免残留表项误判)
# - 每个服务输出重定向到 .start-logs\*.log, 启动失败可直接看日志
# - 子进程用数组参数 + -WorkingDirectory + 绝对路径, 规避路径含特殊字符的解析问题
# ============================================================

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
$LogDir = Join-Path $Root ".start-logs"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# 真实 TCP 连接探测端口是否被占用 (比 netstat 文本匹配可靠)
function Test-PortInUse([int]$port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $ar = $client.BeginConnect('127.0.0.1', $port, $null, $null)
        $ok = $ar.AsyncWaitHandle.WaitOne(500, $false)
        if ($ok -and $client.Connected) {
            $client.EndConnect($ar); $client.Close(); return $true
        }
        $client.Close(); return $false
    } catch {
        return $false
    }
}

# 轮询 HTTP 端点直到就绪或超时
function Wait-Http([string]$url, [int]$timeoutSec) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest -Uri $url -TimeoutSec 2 -UseBasicParsing | Out-Null
            return $true
        } catch { Start-Sleep -Seconds 2 }
    }
    return $false
}

# 启动失败时打印日志尾部, 方便定位
function Show-LogTail([string]$path) {
    if (Test-Path $path) {
        Write-Host "  --- 日志尾部 ($path) ---" -ForegroundColor DarkGray
        Get-Content $path -Tail 15
    }
}

# 在最小化窗口里启动一个服务, 输出写入日志
function Start-Service([string]$workDir, [string]$command, [string]$logFile) {
    Start-Process powershell -WorkingDirectory $workDir -ArgumentList @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Minimized',
        '-Command', "$command *>> '$logFile'"
    )
}

# ---------- 1. Qdrant ----------
$qdrantLog = Join-Path $LogDir "qdrant.log"
if (Test-PortInUse 7333) {
    Write-Host "[1/3] Qdrant 已在运行 (7333), 跳过" -ForegroundColor Green
} else {
    Write-Host "[1/3] 启动 Qdrant (7333)..." -ForegroundColor Cyan
    $qdrantDir = Join-Path $Root "qdrant"
    $qdrantExe = Join-Path $qdrantDir "qdrant.exe"
    Start-Service $qdrantDir "`$env:QDRANT__SERVICE__HTTP_PORT='7333'; `$env:QDRANT__SERVICE__GRPC_PORT='7334'; & '$qdrantExe'" $qdrantLog
    if (-not (Wait-Http "http://127.0.0.1:7333/healthz" 20)) {
        Write-Host "  Qdrant 启动超时, 请检查日志:" -ForegroundColor Yellow
        Show-LogTail $qdrantLog
    }
}

# ---------- 2. 后端 ----------
$backendLog = Join-Path $LogDir "backend.log"
if (Test-PortInUse 9200) {
    Write-Host "[2/3] 后端已在运行 (9200), 跳过" -ForegroundColor Green
} else {
    Write-Host "[2/3] 启动后端 FastAPI (9200)..." -ForegroundColor Cyan
    $py = Join-Path $Root ".venv-xinyu-backend\Scripts\python.exe"
    $runPy = Join-Path $Root "xinyu-backend\run.py"
    Start-Service $Root "& '$py' '$runPy'" $backendLog
    if (-not (Wait-Http "http://127.0.0.1:9200/health" 40)) {
        Write-Host "  后端启动超时, 请检查日志:" -ForegroundColor Yellow
        Show-LogTail $backendLog
    }
}

# ---------- 3. 前端 ----------
$frontendLog = Join-Path $LogDir "frontend.log"
if (Test-PortInUse 5280) {
    Write-Host "[3/3] 前端已在运行 (5280), 跳过" -ForegroundColor Green
} else {
    Write-Host "[3/3] 启动前端 Vite (5280)..." -ForegroundColor Cyan
    $webDir = Join-Path $Root "xinyu-web"
    Start-Service $webDir "npm run dev" $frontendLog
    if (-not (Wait-Http "http://localhost:5280/" 60)) {
        Write-Host "  前端启动超时, 请检查日志:" -ForegroundColor Yellow
        Show-LogTail $frontendLog
    }
}

# ---------- 4. 打开浏览器 ----------
Write-Host "`n全部就绪, 打开 http://localhost:5280" -ForegroundColor Green
Write-Host "服务日志目录: $LogDir" -ForegroundColor DarkGray
Start-Process "http://localhost:5280"
