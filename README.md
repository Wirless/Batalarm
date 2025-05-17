# Battery Alarm App
http://idler.live/batalarm.apk direct download of latest build


A simple Android app that monitors your battery level and alerts you when it gets low.

## Features

- Background battery monitoring
- Customizable battery threshold for alarm (5% to 50%)
- Plays alarm sound when battery level drops below the threshold
- Adaptive monitoring intervals (checks more frequently as battery level decreases)
- Simple UI to control settings

## How It Works

1. The app monitors your battery level in the background
2. When above 30%, it checks every 5 minutes
3. When between 30% and your alarm threshold, it checks every 1-2 minutes depending on how fast the battery is depleting
4. When the battery level drops below your set threshold (default 15%), it plays an alarm sound and shows a notification

## Setup

Before running the app, you need to:

1. Add an MP3 sound file for the alarm:
   - Name it `chargeme.mp3`
   - Place it in `app/src/main/res/raw/` directory

## Permissions

The app requires the following permissions:
- `FOREGROUND_SERVICE`: To run the alarm service in the foreground
- `POST_NOTIFICATIONS`: To display notifications when the battery is low
- `FOREGROUND_SERVICE_SPECIAL_USE`: For foreground service operation

## Usage

1. Open the app
2. Enable "Background Monitoring" to start monitoring your battery
3. Set your desired battery threshold using the slider
4. Enable/disable the alarm as needed
5. When the alarm is playing, you can stop it by pressing the "Stop Alarm" button 
