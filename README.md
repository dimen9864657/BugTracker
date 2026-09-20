# Bug Tracker

An offline first Android bug tracker built for CS 4405.

## Features
- Create, read, update and delete issue tickets
- Works offline. Data is stored on the phone with Room
- Syncs both ways with a REST server using Retrofit
- Failed syncs retry with WorkManager and exponential backoff
- Form state survives rotation with ViewModel and SavedStateHandle

## Git workflow
- `main` holds stable code
- `feature/room-persistence` and `feature/retrofit-sync` hold feature work
- `hotfix/empty-title-crash` holds the urgent fix
- Tags `v1.0` and `v1.1` mark releases

## Setup
Set your own mockapi base URL in `ApiClient.BASE_URL`.