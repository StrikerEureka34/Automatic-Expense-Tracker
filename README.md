# AutoFlow Expense Tracking Pipeline

## Overview

AutoFlow now includes a comprehensive pipeline that continuously monitors notifications, camera, and gallery inputs to automatically parse expenses using OpenRouter LLM API and store them in a local database with Firebase sync.

## Features

### 🔔 Notification Monitoring
- Continuously monitors notifications from payment apps (Google Pay, PhonePe, Paytm, etc.)
- Uses OpenRouter API with multiple LLM models for intelligent parsing
- Automatically categorizes expenses based on content
- Supports multiple languages and currency formats

### 📸 Camera & Gallery Monitoring
- Monitors camera captures and gallery additions for receipt images
- Automatically processes new images using OCR (Optical Character Recognition)
- Uses OpenRouter vision models to extract structured expense data from receipt images
- Processes images within minutes of capture/addition

### 🤖 OpenRouter LLM-Powered Parsing
- Uses OpenRouter API for access to multiple LLM models (GPT-4, Claude, Llama, etc.)
- Supports both text and image parsing for maximum accuracy
- Extracts merchant names, amounts, categories, descriptions, and item lists
- Provides confidence scores for parsed data
- Falls back to basic regex parsing if API is unavailable

### 💾 Local Database with Firebase Sync
- Room database for local storage and offline access
- Automatic synchronization with Firebase when online
- Tracks processing status and sync status for each expense
- Supports data recovery and conflict resolution

### ⚙️ Background Processing
- WorkManager for reliable background task execution
- Foreground service for real-time media monitoring
- Efficient battery usage with smart scheduling
- Automatic retry mechanisms for failed operations

## Setup Instructions

### 1. Prerequisites
- Android device running API 30 or higher
- OpenRouter API key (supports multiple models)
- Firebase project setup

### 2. Configuration

The OpenRouter API key is pre-configured for immediate use. If you want to use your own key, add it to `gradle.properties`:
```properties
OPENROUTER_API_KEY=your_openrouter_api_key_here
```

**Current Model**: Google Gemini 2.5 Flash (Free tier) - Optimized for both text and image processing

### 3. Enable Notification Access

1. Go to Settings > Apps > AutoFlow > Special app access
2. Select "Notification access"
3. Enable notification access for AutoFlow

## OpenRouter Models

The pipeline is configured to use **Google Gemini 2.5 Flash (Free)** for optimal performance:

### Current Model Configuration:
- **Text Parsing**: Gemini 2.5 Flash - Fast and accurate for notifications
- **Image/Receipt Processing**: Gemini 2.5 Flash - Advanced vision capabilities
- **Cost**: Free tier with generous limits
- **Performance**: Optimized for expense parsing tasks

### Alternative Models Available:
- **GPT-4o** - Premium option for highest accuracy
- **Claude 3.5 Sonnet** - Excellent reasoning capabilities
- **Llama 3.1 8B** - Open source alternative
- **GPT-4o Mini** - Fast and cost-effective option

## Components

- **OpenRouterApiClient**: Handles API calls to multiple LLM models
- **ExpenseNotificationListenerService**: Monitors financial app notifications
- **MediaMonitoringService**: Watches camera/gallery for receipt images  
- **LLMExpenseParser**: AI-powered expense parsing using OpenRouter
- **ExpenseRepository**: Local Room database with Firebase sync
- **AutoFlowManager**: Coordinates all monitoring services
- **ExpenseWorkManager**: Background processing and sync jobs

## Usage

Once set up, AutoFlow automatically monitors, processes, and stores expenses from notifications and images. The system intelligently chooses the best model for each parsing task and provides detailed confidence scores.

### Key Advantages of OpenRouter:
- **Multiple Models**: Access to best-in-class models from different providers
- **Cost Effective**: Pay only for what you use with competitive pricing
- **Reliability**: Automatic failover between models
- **Vision Support**: Advanced image understanding for receipt processing
- **Flexibility**: Easy model switching based on accuracy needs
