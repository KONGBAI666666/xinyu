# ============================================================
# 心屿 XinYu · 一键启动脚本 (本地开发)
# 用法: 右键"使用 PowerShell 运行", 或在终端执行 .\start.ps1
# 依次启动: Qdrant(7333) → 后端(9200) → 前端(5280), 并自动打开浏览器
# ============================================================

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

function Test-Port($port) {
    (netstat -ano | Select-String ":$port\s.*LISTENING").Count -gt 0
}

# ---------- 1. Qdrant ----------
if (Test-Port 7333) {
    Write-Host "[1/3] Qdrant 已在运行 (7333), 跳过" -ForegroundColor Green
} else {
    Write-Host "[1/3] 启动 Qdrant (7333)..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-ExecutionPolicy Bypass -WindowStyle Minimized -Command `$env:QDRANT__SERVICE__HTTP_PORT='7333'; `$env:QDRANT__SERVICE__GRPC_PORT='7334'; Set-Location '$Root\qdrant'; .\qdrant.exe"
    Start-Sleep -Seconds 4
}

# ---------- 2. 后端 ----------
if (Test-Port 9200) {
    Write-Host "[2/3] 后端已在运行 (9200), 跳过" -ForegroundColor Green
} else {
    Write-Host "[2/3] 启动后端 FastAPI (9200)..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-ExecutionPolicy Bypass -WindowStyle Minimized -Command Set-Location '$Root'; & '.\.venv-xinyu-backend\Scripts\python.exe' 'xinyu-backend\run.py'"
    # 等后端就绪 (最多 30 秒)
    $ok = $false
    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep -Seconds 2
        try {
            Invoke-RestMethod -Uri "http://127.0.0.1:9200/health" -TimeoutSec 2 | Out-Null
            $ok = $true; break
        } catch {}
    }
    if (-not $ok) { Write-Host "  后端启动超时, 请检查弹出的最小化窗口" -ForegroundColor Yellow }
}

# ---------- 3. 前端 ----------
if (Test-Port 5280) {
    Write-Host "[3/3] 前端已在运行 (5280), 跳过" -ForegroundColor Green
} else {
    Write-Host "[3/3] 启动前端 Vite (5280)..." -ForegroundColor Cyan
    Start-Process powershell -ArgumentList "-ExecutionPolicy Bypass -WindowStyle Minimized -Command Set-Location '$Root\xinyu-web'; npm run dev"
    # 等前端就绪 (最多 40 秒)
    $ok = $false
    for ($i = 0; $i -lt 20; $i++) {
        Start-Sleep -Seconds 2
        try {
            Invoke-WebRequest -Uri "http://localhost:5280/" -TimeoutSec 2 -UseBasicParsing | Out-Null
            $ok = $true; break
        } catch {}
    }
    if (-not $ok) { Write-Host "  前端启动超时, 请检查弹出的最小化窗口" -ForegroundColor Yellow }
}

# ---------- 4. 打开浏览器 ----------
Write-Host "`n全部就绪, 打开 http://localhost:5280" -ForegroundColor Green
Start-Process "http://localhost:5280"
