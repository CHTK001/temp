# run-detection-tests.ps1
# 安全检测类 Example 批量真机验证脚本（可重复执行）。
#
# 用法：
#   powershell -File run-detection-tests.ps1 [-Image <图片路径>] [-OutDir <输出目录>]
# 不传 -Image 时自动取 Windows 壁纸。
#
# 覆盖：SafetyHelmet / FireSmoke / ReflectiveClothes / FaceMaskDetector /
#       SealInspection / YoloWorldBatchDraw(目录模式)
# 说明：部分模型首次运行会按 downloadUrl 自动下载权重（走 hf-mirror）。

param(
    [string]$Image = "",
    [string]$OutDir = "$env:TEMP\det-out"
)

$ErrorActionPreference = 'Continue'
$starter = Split-Path -Parent $MyInvocation.MyCommand.Path

# 0) 编译（幂等）
mvn -o -q -pl . compile
if ($LASTEXITCODE -ne 0) { Write-Host "[ABORT] example-starter 编译失败"; exit 1 }

# 1) 类路径
mvn -o -q dependency:build-classpath "-Dmdep.outputFile=$env:TEMP\det-cp.txt"
$deps = (Get-Content "$env:TEMP\det-cp.txt" -Raw).Trim().Replace('\', '/')
$cls  = (($starter) + "\target\classes").Replace('\', '/')

# 2) 测试图
if (-not $Image) {
    $Image = Get-ChildItem "C:\Windows\Web" -Recurse -Filter *.jpg -ErrorAction SilentlyContinue |
             Select-Object -First 1 -ExpandProperty FullName
}
if (-not $Image -or -not (Test-Path $Image)) { Write-Host "[ABORT] 无测试图"; exit 1 }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$imgArg = $Image.Replace('\', '/')

# 3) 批量执行
$cases = @(
    @{ N = 'SafetyHelmetExample';      Args = @($imgArg, ("$($OutDir.Replace('\','/'))/safety.png"), "0.25") },
    @{ N = 'FireSmokeExample';         Args = @($imgArg, ("$($OutDir.Replace('\','/'))/fire.png"),  "0.25") },
    @{ N = 'ReflectiveClothesExample'; Args = @($imgArg, ("$($OutDir.Replace('\','/'))/reflect.png"), "0.25") },
    @{ N = 'FaceMaskDetectorExample';  Args = @($imgArg, ("$($OutDir.Replace('\','/'))/mask.png"),  "0.25") },
    @{ N = 'SealInspectionExample';    Args = @($imgArg, ("$($OutDir.Replace('\','/'))/seal.png"),  "0.25") }
)

$pass = 0; $fail = 0; $failNames = @()
foreach ($c in $cases) {
    $n = $c.N
    Set-Content -Path "$env:TEMP\d-run.args" -Value (
        @('-Xmx1g',
          '--add-opens=java.base/java.lang=ALL-UNNAMED',
          '--enable-native-access=ALL-UNNAMED',
          '-cp', "`"$cls;$deps`"",
          "com.chua.example.onnx.$n") + $c.Args) -Encoding ascii
    Write-Host "── $n"
    & java "@$env:TEMP\d-run.args" 2>&1 |
        Select-String '\[PASS\]|\[FAIL\]|目标|检测|完成' |
        Select-Object -First 3 | ForEach-Object { Write-Host ("   " + $_.Line) }
    if ($LASTEXITCODE -eq 0) { $pass++; Write-Host "   exit=0 ✅" }
    else { $fail++; $failNames += $n; Write-Host "   exit=$LASTEXITCODE ❌" }
}

Write-Host "===== 汇总 通过=$pass 失败=$fail ====="
if ($failNames) { Write-Host ("失败清单: " + ($failNames -join ', ')) }
exit $(if ($fail -gt 0) { 1 } else { 0 })
