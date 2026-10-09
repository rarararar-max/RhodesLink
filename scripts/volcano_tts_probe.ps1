# 火山引擎（豆包语音）V3 单向流式 TTS 原始探测脚本
#
# 用途：在不依赖 App 的情况下，把“一次真实请求”的完整证据打出来，交给火山客服排障：
#   1) HTTP 状态码 与 全部响应头（重点是 X-Tt-Logid）
#   2) 客户端 X-Api-Request-Id（本脚本生成）
#   3) 逐帧原始响应体（成功帧 / 结束帧 / 失败帧都原样打印）
#   4) 请求用的 X-Api-Resource-Id、speaker、model 与 API Key 指纹（不打印完整密钥）
#
# 用法（PowerShell 7 / Windows PowerShell 5.1 均可）：
#   .\volcano_tts_probe.ps1 -ApiKey "你的API Key" -Speaker "S_xxxxxxxx"
#   .\volcano_tts_probe.ps1 -ApiKey "..." -Speaker "S_xxx" -ResourceId "seed-icl-1.0"
#   .\volcano_tts_probe.ps1 -ApiKey "..." -Speaker "S_xxx" -Mode sse
#   .\volcano_tts_probe.ps1 -ApiKey "..." -Speaker "S_xxx" -Text "你好，测试一下。"
#
# 说明：
# - 默认走 HTTP Chunked（/api/v3/tts/unidirectional），-Mode sse 走 /sse。
# - 本脚本只发一次请求（App 是按句子分段发多次）。
# - 失败时把最后打印的“===== 发给客服的证据 =====”整段复制过去即可。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [Parameter(Mandatory = $true)][string]$Speaker,
    [string]$ResourceId = "seed-icl-2.0",
    [string]$Text = "你好，这是一次语音合成测试。",
    [ValidateSet("chunked", "sse")][string]$Mode = "chunked",
    [string]$Format = "mp3",
    [int]$SampleRate = 24000,
    [int]$SpeechRate = 0,
    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"

$endpoint = if ($Mode -eq "sse") {
    "https://openspeech.bytedance.com/api/v3/tts/unidirectional/sse"
} else {
    "https://openspeech.bytedance.com/api/v3/tts/unidirectional"
}

function New-RequestId {
    return [guid]::NewGuid().ToString()
}

function Get-KeyFingerprint([string]$key) {
    $trimmed = $key.Trim()
    if ($trimmed.Length -le 8) { return "长度 $($trimmed.Length)（过短，可能填错）" }
    return "$($trimmed.Substring(0,6))…（长度 $($trimmed.Length)）"
}

$requestId = New-RequestId
# 复刻 2.0 建议显式带 model；1.0 不要带（传了会报 InvalidModel），与 App 内逻辑一致。
$model = if ($ResourceId -like "*icl-2.0*") { "seed-tts-2.0-standard" } else { $null }

$body = @{
    req_params = @{
        text         = $Text
        speaker      = $Speaker
        audio_params = @{
            format      = $Format
            sample_rate = $SampleRate
            speech_rate = $SpeechRate
        }
        additions    = '{"disable_markdown_filter":true}'
    }
}
if ($model) { $body.req_params.model = $model }

$headers = @{
    "X-Api-Key"                            = $ApiKey
    "X-Api-Resource-Id"                    = $ResourceId
    "X-Api-Request-Id"                     = $requestId
    "X-Control-Require-Usage-Tokens-Return" = "*"
}

Write-Host "===== 请求 =====" -ForegroundColor Cyan
Write-Host "URL              : $endpoint"
Write-Host "资源版本         : $ResourceId"
Write-Host "音色(Speaker)    : $Speaker"
Write-Host "model            : $(if ($model) { $model } else { "（不传，1.0 规则）" })"
Write-Host "文本             : $Text"
Write-Host "客户端请求ID     : $requestId"
Write-Host "API Key 指纹     : $(Get-KeyFingerprint $ApiKey)"
Write-Host ""

$json = $body | ConvertTo-Json -Depth 6 -Compress
$statusCode = $null
$statusText = ""
$responseHeaders = @{}
$lines = New-Object System.Collections.Generic.List[string]

try {
    # -SkipHttpErrorCheck 需要 PS7；PS5.1 下用 try/catch 兜 WebException 的响应体。
    $response = Invoke-WebRequest -Uri $endpoint -Method Post -Headers $headers `
        -ContentType "application/json; charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes($json)) `
        -UseBasicParsing -TimeoutSec 60
    $statusCode = [int]$response.StatusCode
    $statusText = $response.StatusDescription
    foreach ($key in $response.Headers.Keys) { $responseHeaders[$key] = $response.Headers[$key] }
    $lines.AddRange([string[]]($response.Content -split "`r?`n"))
}
catch {
    $webResponse = $null
    if ($_.Exception.Response) { $webResponse = $_.Exception.Response }
    if ($webResponse) {
        try { $statusCode = [int]$webResponse.StatusCode } catch { }
        if ($webResponse.Headers) {
            foreach ($key in $webResponse.Headers.AllKeys) { $responseHeaders[$key] = $webResponse.Headers[$key] }
        }
        # 4xx/5xx 的响应体才是客服要的证据（里面就是 code/message）。
        # Windows PowerShell 5.1 下 Invoke-WebRequest 抛异常后 Response 流往往已经不可读，
        # 所以优先用 $_.ErrorDetails.Message（PowerShell 会把错误响应体放在这里），再退回原始流。
        $bodyText = ""
        try { $bodyText = [string]$_.ErrorDetails.Message } catch { }
        if ([string]::IsNullOrWhiteSpace($bodyText)) {
            try {
                $stream = $webResponse.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $bodyText = $reader.ReadToEnd()
                    $reader.Close()
                }
            } catch { }
        }
        if (-not [string]::IsNullOrWhiteSpace($bodyText)) {
            $lines.AddRange([string[]]($bodyText -split "`r?`n"))
        }
    }
    if (-not $statusCode) {
        Write-Host "请求直接失败（没拿到 HTTP 响应）：$($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "===== 响应 =====" -ForegroundColor Cyan
Write-Host "HTTP 状态        : $(if ($statusCode) { "$statusCode $statusText" } else { "无响应" })"
if ($responseHeaders.Count -gt 0) {
    Write-Host "响应头           :"
    foreach ($key in ($responseHeaders.Keys | Sort-Object)) {
        $value = ($responseHeaders[$key] -join ", ")
        Write-Host "  $key = $value"
    }
}

$logId = ""
foreach ($candidate in @("X-Tt-Logid", "X-Tt-Log-Id", "X-Api-Request-Id", "X-Request-Id")) {
    if ($responseHeaders.ContainsKey($candidate)) { $logId = ($responseHeaders[$candidate] -join ","); break }
}

Write-Host ""
Write-Host "===== 逐帧响应体 =====" -ForegroundColor Cyan
$audioBase64 = New-Object System.Text.StringBuilder
$frameIndex = 0
$errorFrames = New-Object System.Collections.Generic.List[string]
$lastFrame = ""
foreach ($line in $lines) {
    $trimmed = $line.Trim()
    if (-not $trimmed) { continue }
    Write-Host "[$frameIndex] $trimmed"
    $frameIndex++
    $lastFrame = $trimmed
    $payload = $trimmed
    if ($payload.StartsWith("data:")) { $payload = $payload.Substring(5).Trim() }
    try {
        $obj = $payload | ConvertFrom-Json
        # App 已兼容两种信封：顶层 code/data，或 V3 的 header.code + payload/data
        $code = $null
        if ($obj.PSObject.Properties.Name -contains "code") { $code = $obj.code }
        elseif ($obj.header -and ($obj.header.PSObject.Properties.Name -contains "code")) { $code = $obj.header.code }
        $message = ""
        if ($obj.PSObject.Properties.Name -contains "message") { $message = $obj.message }
        elseif ($obj.header -and ($obj.header.PSObject.Properties.Name -contains "message")) { $message = $obj.header.message }

        $audio = $null
        if ($obj.data -is [string]) { $audio = $obj.data }
        elseif ($obj.data -and $obj.data.audio) { $audio = $obj.data.audio }
        elseif ($obj.payload -and $obj.payload.audio) { $audio = $obj.payload.audio }
        if ($audio) { [void]$audioBase64.Append($audio) }

        if ($code -ne $null -and $code -ne 0 -and $code -ne 20000000 -and $code -ne 3000) {
            $errorFrames.Add("code=$code message=$message")
        }
    } catch {
        Write-Host "    （这一行不是合法 JSON，原样保留给客服）" -ForegroundColor Yellow
    }
}

$audioBytes = 0
if ($audioBase64.Length -gt 0) {
    try { $audioBytes = [Convert]::FromBase64String($audioBase64.ToString()).Length } catch { $audioBytes = -1 }
    if ($OutFile) {
        [IO.File]::WriteAllBytes($OutFile, [Convert]::FromBase64String($audioBase64.ToString()))
        Write-Host ""
        Write-Host "音频已写入：$OutFile（$audioBytes 字节）" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "===== 发给客服的证据 =====" -ForegroundColor Cyan
Write-Host "时间             : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')（本机时区，建议换算成北京时间）"
Write-Host "接口             : $endpoint"
Write-Host "HTTP 状态        : $(if ($statusCode) { $statusCode } else { "无响应" })"
Write-Host "X-Api-Resource-Id: $ResourceId"
Write-Host "Speaker          : $Speaker"
Write-Host "model            : $(if ($model) { $model } else { "（未传）" })"
Write-Host "X-Tt-Logid(服务端): $(if ($logId) { $logId } else { "响应头里没有，请把上面的完整响应头一起发" })"
Write-Host "客户端请求ID     : $requestId"
Write-Host "API Key 指纹     : $(Get-KeyFingerprint $ApiKey)"
Write-Host "解码音频字节数   : $audioBytes"
if ($errorFrames.Count -gt 0) {
    Write-Host "错误帧           : $($errorFrames -join ' | ')" -ForegroundColor Red
} else {
    Write-Host "错误帧           : 无"
}
Write-Host "最后一帧         : $lastFrame"
Write-Host ""
Write-Host "把本段（以及上面的“逐帧响应体”）整段发给豆包语音客服，并说明：" -ForegroundColor Yellow
Write-Host "  1. 这个 X-Api-Key 是否已开通对应资源（复刻 2.0 → seed-icl-2.0）？"
Write-Host "  2. Speaker $Speaker 当前 State（Training/Success/Active/Expired/Reclaimed）与所属版本（1.0/2.0）？"
Write-Host "  3. LogID/请求ID 这一次被拒的具体错误码与原因？"
