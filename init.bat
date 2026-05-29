@echo off
chcp 65001 >nul
title Minecraft 插件初始化工具

echo ====== Minecraft 插件初始化脚本 ======
echo.
echo 本脚本将初始化当前文件夹为一个 Git 仓库并推送到 GitHub
echo.

:: 设置代理（如果不需要，把下面两行注释掉）
set PROXY_PORT=7890
echo 设置代理：127.0.0.1:%PROXY_PORT%
git config --global http.proxy http://127.0.0.1:%PROXY_PORT%
git config --global https.proxy http://127.0.0.1:%PROXY_PORT%
echo.

:: 设置 Git 身份
echo 设置 Git 身份...
set /p GIT_NAME=请输入 GitHub 用户名（如 koteyoseruyi）： 
set /p GIT_EMAIL=请输入 GitHub 邮箱（如 you@example.com）： 

git config --global user.name "%GIT_NAME%"
git config --global user.email "%GIT_EMAIL%"
echo ✅ Git 身份已设置
echo.

:: 初始化仓库
echo 正在初始化 Git 仓库...
git init
echo ✅ 仓库已初始化
echo.

:: 添加所有文件
echo 正在添加文件...
git add .
echo ✅ 文件已添加
echo.

:: 首次提交
echo 正在创建首次提交...
git commit -m "首次提交"
echo ✅ 首次提交完成
echo.

:: 设置远程仓库
set /p REPO_URL=请输入 GitHub 仓库地址（如 https://github.com/koteyoseruyi/ScratchCardFumino.git）： 

:: 检查是否已有远程仓库
git remote remove origin 2>nul
git remote add origin %REPO_URL%
echo ✅ 远程仓库已关联
echo.

:: 重命名分支为 main
git branch -M main
echo ✅ 分支已重命名为 main
echo.

:: 推送到 GitHub
echo 正在推送到 GitHub（需要输入 Personal Access Token）...
git push -u origin main
if %errorlevel% equ 0 (
    echo ✅ 初始化成功！你的插件已上传到 GitHub
) else (
    echo ❌ 推送失败，请检查：
    echo   - 仓库地址是否正确
    echo   - 是否使用 Personal Access Token 作为密码
    echo   - 网络是否正常
)
echo.

:: 取消代理
echo 正在取消代理设置...
git config --global --unset http.proxy 2>nul
git config --global --unset https.proxy 2>nul
echo ✅ 代理已取消
echo.

echo ====== 初始化完成 ======
echo.
pause
