class LanguageModel {
  final String code;
  final String name;
  final String nativeName;

  const LanguageModel({
    required this.code,
    required this.name,
    required this.nativeName,
  });
}

const List<LanguageModel> supportedLanguages = [
  LanguageModel(code: 'en', name: 'English', nativeName: 'English (Default)'),
  LanguageModel(code: 'es', name: 'Spanish', nativeName: 'Español'),
  LanguageModel(code: 'de', name: 'German', nativeName: 'Deutsch'),
  LanguageModel(code: 'fr', name: 'French', nativeName: 'Français'),
  LanguageModel(code: 'ar', name: 'Arabic', nativeName: 'العربية'),
  LanguageModel(code: 'ru', name: 'Russian', nativeName: 'Русский'),
  LanguageModel(code: 'hi', name: 'Hindi', nativeName: 'हिन्दी'),
  LanguageModel(code: 'pt', name: 'Portuguese', nativeName: 'Português'),
  LanguageModel(code: 'ja', name: 'Japanese', nativeName: '日本語'),
  LanguageModel(code: 'ko', name: 'Korean', nativeName: '한국어'),
  LanguageModel(code: 'zh', name: 'Chinese', nativeName: '中文'),
  LanguageModel(code: 'tr', name: 'Turkish', nativeName: 'Türkçe'),
  LanguageModel(code: 'nl', name: 'Dutch', nativeName: 'Nederlands'),
  LanguageModel(code: 'vi', name: 'Vietnamese', nativeName: 'Tiếng Việt'),
  LanguageModel(code: 'id', name: 'Indonesian', nativeName: 'Bahasa Indonesia'),
  LanguageModel(code: 'th', name: 'Thai', nativeName: 'ไทย'),
  LanguageModel(code: 'ms', name: 'Malay', nativeName: 'Bahasa Melayu'),
  LanguageModel(code: 'pa', name: 'Punjabi', nativeName: 'ਪੰਜਾਬੀ'),
];
