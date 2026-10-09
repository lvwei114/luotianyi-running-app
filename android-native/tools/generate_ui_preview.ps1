$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$taskProject=Split-Path $PSScriptRoot -Parent
$taskOutput=Join-Path $taskProject 'docs\images\ui-cleanup'
New-Item -ItemType Directory -Path $taskOutput -Force | Out-Null
$taskScreens=@(
    @('首页','home'),@('记录与分析','run'),
    @('跑步：距离与配速','workout-moving-small'),@('跑步：运动控制','workout-controls-small'),
    @('记录详情：指标','record-detail-top'),@('记录详情：删除按钮','record-detail'),
    @('设置：顶部','settings'),@('设置：运动提醒','settings-reminders'),
    @('设置：音量与节拍器','settings-audio'),@('设置：健康与关于','settings-about')
)
foreach($taskScreen in $taskScreens){
    Copy-Item -LiteralPath (Join-Path $taskProject ('app\build\qa\native-'+$taskScreen[1]+'.png')) -Destination (Join-Path $taskOutput ($taskScreen[1]+'.png'))
}
function New-PreviewStrip($taskEntries,$taskName){
    $taskWidth=390;$taskGap=16;$taskHeader=52;$taskHeight=0
    $taskImages=@($taskEntries | ForEach-Object {[System.Drawing.Image]::FromFile((Join-Path $taskOutput ($_[1]+'.png')))})
    try {
        foreach($taskImage in $taskImages){$taskHeight=[Math]::Max($taskHeight,[int]($taskImage.Height*$taskWidth/$taskImage.Width))}
        $taskCanvas=[System.Drawing.Bitmap]::new(($taskWidth+$taskGap)*$taskImages.Count+$taskGap,$taskHeight+$taskHeader+$taskGap)
        $taskDrawing=[System.Drawing.Graphics]::FromImage($taskCanvas)
        $taskFont=[System.Drawing.Font]::new('Microsoft YaHei',16,[System.Drawing.FontStyle]::Regular,[System.Drawing.GraphicsUnit]::Pixel)
        $taskBrush=[System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(102,204,255))
        try {
            $taskDrawing.Clear([System.Drawing.Color]::FromArgb(16,17,26))
            $taskDrawing.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            for($taskIndex=0;$taskIndex -lt $taskImages.Count;$taskIndex++){
                $taskX=$taskGap+($taskWidth+$taskGap)*$taskIndex
                $taskDrawing.DrawString($taskEntries[$taskIndex][0],$taskFont,$taskBrush,$taskX,18)
                $taskImage=$taskImages[$taskIndex]
                $taskDrawing.DrawImage($taskImage,$taskX,$taskHeader,$taskWidth,[int]($taskImage.Height*$taskWidth/$taskImage.Width))
            }
            $taskCanvas.Save((Join-Path $taskOutput $taskName),[System.Drawing.Imaging.ImageFormat]::Png)
        } finally {$taskBrush.Dispose();$taskFont.Dispose();$taskDrawing.Dispose();$taskCanvas.Dispose()}
    } finally {foreach($taskImage in $taskImages){$taskImage.Dispose()}}
}
New-PreviewStrip @($taskScreens[0],$taskScreens[1]) 'home-records.png'
New-PreviewStrip @($taskScreens[2],$taskScreens[3],$taskScreens[5]) 'workout-detail.png'
New-PreviewStrip @($taskScreens[6],$taskScreens[7],$taskScreens[9]) 'settings-overview.png'
$taskFigures=($taskScreens | ForEach-Object {'<figure><figcaption>'+$_[0]+'</figcaption><a href="images/ui-cleanup/'+$_[1]+'.png"><img src="images/ui-cleanup/'+$_[1]+'.png" alt="'+$_[0]+'" loading="lazy"></a></figure>'}) -join "`n"
$taskHtml=@"
<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>洛天依陪跑 · 界面修改预览</title>
<style>body{margin:0;padding:28px;background:#10111a;color:#fff6ec;font-family:'Microsoft YaHei',sans-serif}h1{font-size:26px;color:#66ccff}p{color:#a6afbf;line-height:1.7}.gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,390px));gap:28px}figure{margin:0}figcaption{margin-bottom:12px;color:#66ccff}img{width:100%;height:auto;border-radius:16px;border:1px solid #454c5d}a{color:#66ccff}</style>
<h1>洛天依陪跑 · 界面修改预览</h1><p>新版清单的 6 项调整及方案 B 首页问候语与连续天数已包含在 v0.1.6 测试版中。以下为原生界面渲染图，训练记录使用示例数据。点击图片查看原尺寸。</p><div class="gallery">$taskFigures</div></html>
"@
Set-Content -LiteralPath (Join-Path $taskProject 'docs\界面预览.html') -Value $taskHtml -Encoding utf8
Write-Output $taskOutput
