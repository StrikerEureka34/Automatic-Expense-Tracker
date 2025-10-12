# AutoFlow — AI-Powered Expense Tracking

## Overview

AutoFlow is an intelligent expense tracking system that leverages **Machine Learning and Deep Learning** to automatically capture, parse, and analyze expenses from multiple sources. The app combines advanced AI technologies including **OpenRouter LLM APIs**, **ML Kit OCR**, and **smart trend analysis** to provide seamless expense management.

## 🚀 Key Features

### ⚡ Intelligent Expense Parsing (ML/OCR & LLM)
- **Multi-Modal AI Processing**: Combines Google ML Kit OCR for initial text extraction with OpenRouter's advanced LLM models for intelligent parsing
- **Smart Context Understanding**: Uses vision-language models to understand receipt structure, merchant context, and transaction details
- **High-Confidence Extraction**: Provides confidence scores and falls back to regex patterns when AI confidence is low
- **Multi-Currency Support**: Automatically detects and processes different currencies (INR, USD, EUR, GBP)

### 🔔 Smart Notification Monitoring
- **AI-Powered Text Analysis**: Monitors payment app notifications and uses LLMs to extract structured expense data
- **App-Specific Intelligence**: Recognizes patterns from major payment apps (Google Pay, PhonePe, Paytm, Swiggy, Uber, etc.)
- **Real-Time Processing**: Automatically categorizes and stores expenses as notifications arrive
- **Multi-Language Support**: Handles notifications in various languages and formats

### 📸 Computer Vision Receipt Processing
- **Automated Image Monitoring**: Continuously watches camera captures and gallery additions for receipt images
- **Advanced OCR Pipeline**: Uses Google ML Kit for text extraction followed by OpenRouter vision models for structured parsing
- **Receipt Intelligence**: Extracts merchant names, itemized lists, totals, dates, and categorizes based on business type
- **Image Quality Adaptation**: Processes various image qualities and receipt formats

### 📈 Smart Trend Analysis
- **Spending Pattern Recognition**: Analyzes historical expense data to identify spending trends and patterns
- **Interactive Visualizations**: Provides pie charts, bar charts, and line charts for comprehensive spending insights
- **Category-wise Analysis**: Breaks down expenses by categories with intelligent categorization
- **Daily/Monthly Trends**: Tracks spending patterns over time with daily averages and monthly summaries
- **Data-Driven Insights**: Uses local database for fast, offline trend analysis

### 💾 Intelligent Data Management
- **Hybrid Storage Architecture**: Room database for local processing with optional Firebase cloud sync
- **AI-Enhanced Organization**: Automatically categorizes and tags expenses using ML-based classification
- **Conflict Resolution**: Smart sync mechanisms handle offline-online data conflicts
- **Processing State Tracking**: Maintains detailed logs of AI processing confidence and source attribution

### ⚙️ Background Intelligence
- **WorkManager Integration**: Reliable background AI processing with smart scheduling
- **Foreground Vision Service**: Real-time image monitoring with efficient battery optimization
- **Intelligent Retry Logic**: Automatic recovery mechanisms for failed AI processing tasks
- **Performance Optimization**: Adaptive processing based on device capabilities and network conditions

## 🧠 AI/ML Core

AutoFlow's intelligence comes from a sophisticated AI pipeline that processes expenses with human-like understanding:

### **ML Kit OCR Engine**
- Google's state-of-the-art text recognition for receipt and image processing
- Optimized for various fonts, languages, and image qualities
- Real-time text extraction with high accuracy rates

### **OpenRouter LLM Integration**
- **Multi-Model Access**: Leverages cutting-edge models including GPT-4, Claude, Gemini, and Llama
- **Vision-Language Models**: Advanced image understanding for receipt structure analysis
- **Context-Aware Parsing**: Understands merchant context, transaction semantics, and spending patterns
- **Confidence Scoring**: Each AI prediction includes confidence metrics for reliability assessment

### **Intelligent Categorization**
- **Pattern Recognition**: Machine learning-based category detection from merchant names and transaction context
- **Adaptive Learning**: Improves categorization accuracy based on user patterns
- **Fallback Mechanisms**: Robust regex patterns ensure parsing even when AI models are unavailable

## � Setup Instructions
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

## 🤖 AI-Powered OpenRouter Models

AutoFlow leverages **OpenRouter's model ecosystem** for state-of-the-art expense parsing capabilities:

### Current AI Configuration:
- **Text Processing**: `meta-llama/llama-3.2-11b-vision-instruct:free` - Advanced reasoning for notification analysis
- **Vision Processing**: `meta-llama/llama-3.2-11b-vision-instruct:free` - Specialized receipt understanding 
- **Fallback Model**: `qwen/qwen2.5-vl-32b-instruct:free` - Robust backup for complex cases
- **Performance**: Free tier models optimized for expense parsing with high accuracy

### Advanced Model Options:
- **GPT-4 Vision** - Premium accuracy for complex receipts
- **Claude 3.5 Sonnet** - Superior reasoning for transaction context
- **Gemini Pro Vision** - Google's multimodal intelligence
- **Cost Optimization**: Smart model selection based on complexity and accuracy requirements

## 🛠️ Core Components

### AI/ML Processing Stack:
- **`LLMExpenseParser`**: Orchestrates the AI pipeline for expense extraction and categorization
- **`OpenRouterApiClient`**: Manages multi-model API communication with confidence scoring
- **`MediaMonitoringService`**: Computer vision pipeline for receipt processing with ML Kit integration

### Data & Analytics:
- **`ExpenseRepository`**: Intelligent database management with trend computation capabilities  
- **`FirstFragment`**: Advanced dashboard with AI-powered spending analytics and pattern visualization

### Monitoring & Automation:
- **`ExpenseNotificationListenerService`**: Smart notification filtering and real-time AI processing
- **`AutoFlowManager`**: Coordinates all AI services for seamless expense tracking
- **`ExpenseWorkManager`**: Background AI processing with intelligent retry mechanisms

## 📱 Setup Instructions

### 1. Prerequisites
- Android device running API 30+ (Android 11 or higher)
- OpenRouter API key for advanced AI features (optional - basic functionality works without)
- Firebase project setup for cloud sync (optional)

### 2. Configuration

**For immediate use**: The app comes pre-configured with free-tier AI models and works out of the box.

**For custom AI configuration**, add your OpenRouter API key to `gradle.properties`:
```properties
OPENROUTER_API_KEY=your_openrouter_api_key_here
```

**Current AI Models**: 
- Text: `meta-llama/llama-3.2-11b-vision-instruct:free`
- Vision: `meta-llama/llama-3.2-11b-vision-instruct:free` 
- Fallback: `qwen/qwen2.5-vl-32b-instruct:free`

### 3. Enable Required Permissions

**Notification Access** (Essential for AI processing):
1. Go to Settings > Apps > AutoFlow > Special app access
2. Select "Notification access"  
3. Enable notification access for AutoFlow

**Camera & Storage** (For receipt processing):
- Camera permission for direct receipt capture
- Storage permission for gallery image monitoring
- Automatic permission requests on first use

### 4. Build & Install
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 🔍 Troubleshooting & Performance

### AI Processing Issues:
- **Low Confidence Parsing**: App automatically falls back to regex patterns when AI confidence is below 30%
- **Network Issues**: Local processing ensures basic functionality works offline
- **API Rate Limits**: Free-tier models have generous limits; check OpenRouter dashboard for usage

### Performance Optimization:
- **Battery Usage**: Background services are optimized with WorkManager and adaptive scheduling
- **Storage**: Local Room database provides fast access; Firebase sync is optional
- **Processing Speed**: ML Kit OCR runs locally; OpenRouter requests are batched for efficiency

### Common Issues:
- **Permissions**: Ensure notification access is enabled for automatic expense capture
- **Model Selection**: App automatically chooses the best available model based on task complexity
- **Categorization**: AI learns from patterns; manual corrections improve future accuracy

## 💡 Usage & Intelligence

Once configured, AutoFlow operates as an intelligent expense assistant that learns and adapts to your spending patterns:

### **Automatic AI Processing:**
- **Smart Detection**: Automatically identifies expense-related notifications and images
- **Contextual Understanding**: Uses advanced AI to understand merchant context, transaction types, and spending categories
- **Confidence-Based Decisions**: Only processes high-confidence AI predictions; falls back to manual review for ambiguous cases

### **Trend Analysis & Insights:**
- **Pattern Recognition**: Identifies spending trends, category preferences, and unusual transactions
- **Visual Analytics**: Interactive charts show daily/weekly/monthly spending patterns with intelligent categorization
- **Predictive Insights**: Historical data analysis helps identify spending habits and budget optimization opportunities

### **Key AI Advantages:**
- **Multi-Modal Intelligence**: Combines text and vision AI for comprehensive expense understanding
- **Model Flexibility**: Automatic selection of best AI models based on task complexity and accuracy requirements  
- **Cost-Effective Processing**: Smart use of free-tier models with premium fallbacks for complex cases
- **Offline Capability**: Local processing ensures functionality even without internet connectivity
- **Learning Capability**: Improves accuracy over time through pattern recognition and user feedback

## 🎥 Demo Videos & Tutorials

- **Complete App Walkthrough**: [AutoFlow AI Expense Tracking Demo](https://youtube.com/placeholder_app_overview)
- **Notification Feature in Action**: [Smart Notification Processing with AI](https://youtube.com/placeholder_notification_demo)  
- **Camera Capture & AI Parsing**: [Receipt Processing with Computer Vision](https://youtube.com/placeholder_camera_demo)
- **Gallery Upload & Trend Analysis**: [Spending Analytics and Pattern Recognition](https://youtube.com/placeholder_gallery_trends_demo)
- **ML/AI Deep Dive**: [Behind the Scenes: How AutoFlow's AI Works](https://youtube.com/placeholder_ai_deepdive)
- **Setup & Configuration Guide**: [Getting Started with AutoFlow](https://youtube.com/placeholder_setup_guide)

## 🤝 Contributing

We welcome contributions to improve AutoFlow's AI capabilities and features! Please see [`CONTRIBUTING.md`](CONTRIBUTING.md) for:
- How to contribute to the ML/AI pipeline
- Guidelines for improving expense parsing accuracy
- Adding new OpenRouter models and testing procedures
- Bug reporting and feature requests

## 📄 License

This project is licensed under the MIT License - see the [`LICENSE`](LICENSE) file for details.

## 🙏 Acknowledgments

Thanks to my frineds for working with me and continuing to build the project:
- K P Sai praneeth
- Om Dhondge

We are grateful to the following projects and services for their support:
- **OpenRouter** - For providing access to state-of-the-art LLM models
- **Google ML Kit** - For robust OCR capabilities  
- **Firebase** - For seamless cloud synchronization
- **MPAndroidChart** - For beautiful expense visualization charts
