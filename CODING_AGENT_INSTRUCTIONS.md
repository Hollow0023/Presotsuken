# コーディングエージェント用カスタム指示について

このリポジトリには、コーディングエージェント（GitHub Copilot、Cursor、その他のAIアシスタント）向けのカスタム指示ファイルが含まれています。

## 指示ファイルの場所

### 1. メイン指示ファイル
- **`.copilot-instructions.md`** - 最も詳細で包括的な指示
  - プロジェクト概要
  - 技術スタック詳細
  - コーディング規約
  - 業務ドメイン知識
  - アーキテクチャパターン
  - 実装例とベストプラクティス

### 2. GitHub Copilot専用
- **`.github/copilot-instructions.md`** - GitHub Copilot向けの中程度の詳細度

### 3. VS Code専用  
- **`.vscode/copilot-instructions.md`** - VS Code Copilot Extension向けの簡潔版

## 使用方法

これらのファイルは、コーディングエージェントが自動的に参照し、以下の点でコーディング支援を向上させます：

- **プロジェクト固有のパターン** の理解
- **業務ドメイン知識**（飲食店システム）の適用
- **日本語コメント + 英語コード** の命名規約遵守
- **Spring Boot + JPA** のベストプラクティス適用

## 更新について

プロジェクトの要件やパターンが変更された場合は、これらの指示ファイルも併せて更新してください。特に新しい機能やアーキテクチャパターンを追加した際は重要です。

## 対応エージェント

- GitHub Copilot
- GitHub Copilot Chat  
- Cursor AI
- VS Code Copilot
- その他OpenAI/Claude APIベースのコーディングアシスタント