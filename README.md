# PC Remote Control - Android App

## Сборка APK через GitHub Actions

1. Зарегистрируйся на github.com (бесплатно)
2. Создай новый репозиторий (New repository)
3. Загрузи все файлы этой папки
4. Зайди в Actions → Build APK → Run workflow
5. Через 3-5 минут скачай APK из Artifacts

## Запуск сервера на ПК

```
pip install websockets pyautogui psutil pillow
python server.py
```

## Управление из школы (бесплатно)

1. Скачай ngrok: https://ngrok.com/download
2. Зарегистрируйся бесплатно на ngrok.com
3. Запусти: `ngrok tcp 8765`
4. В приложении введи адрес вида: `0.tcp.ngrok.io:12345`
