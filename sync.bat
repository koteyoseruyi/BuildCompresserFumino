@echo off
chcp 65001 >nul
title Minecraft 插件同步工具

echo ====== Minecraft 插件同步脚本 ======
echo.

:: 设置代理（如果不需要代理，可以把下面两行前面加 rem 注释掉）
set PROXY_PORT=7890
echo 设置代理：127.0.0.1:%PROXY_PORT%
git config --global http.proxy http://127.0.0.1:%PROXY_PORT%
git config --global https.proxy http://127.0.0.1:%PROXY_PORT%
echo.

:: 拉取远程更新（解决冲突）
echo 正在拉取远程仓库更新...
git pull origin main --allow-unrelated-histories
if %errorlevel% neq 0 (
    echo ⚠️ 拉取失败，请检查网络或手动处理冲突
    goto END
)
echo ✅ 拉取完成
echo.

:: 显示变更文件
echo 检测到的变更文件：
git status --short
echo.

:: 询问提交说明
set /p COMMIT_MSG=请输入提交说明（直接回车使用默认）： 
if "%COMMIT_MSG%"=="" set COMMIT_MSG=更新插件

:: 添加、提交、推送
echo 正在添加文件...
git add .
echo ✅ 文件已添加
echo.

echo 正在提交...
git commit -m "%COMMIT_MSG%"
echo ✅ 提交完成
echo.

echo 正在推送到 GitHub...
git push
if %errorlevel% equ 0 (
    echo ✅ 推送成功！GitHub 已同步更新
) else (
    echo ❌ 推送失败，请检查网络或 Token
)
echo.

:END
:: 取消代理
echo 正在取消代理设置...
git config --global --unset http.proxy 2>nul
git config --global --unset https.proxy 2>nul
echo ✅ 代理已取消
echo.

echo ====== 操作完成 ======
echo.
pause
