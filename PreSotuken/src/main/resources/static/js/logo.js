document.addEventListener('DOMContentLoaded', function() {
    const logoFileInput = document.getElementById('logoFile');
    const logoBase64Input = document.getElementById('logoBase64');
    const currentLogoPreview = document.getElementById('currentLogo');
    const uploadButton = document.getElementById('uploadButton');
    const logoUploadForm = document.getElementById('logoUploadForm');

    const MAX_FILE_SIZE_BYTES = 100 * 1024; // 100KB

    // ファイル選択時の処理
    logoFileInput.addEventListener('change', function(event) {
        const file = event.target.files[0];

        if (!file) {
            // ファイルが選択されていない場合
            currentLogoPreview.src = currentLogoPreview.dataset.originalSrc || currentLogoPreview.src; // 元の画像に戻す
            uploadButton.disabled = true;
            logoBase64Input.value = '';
            alert('ファイルが選択されていません。');
            return;
        }

        // ファイルサイズのみチェックする
        if (file.size > MAX_FILE_SIZE_BYTES) {
            alert(`ファイルサイズが大きすぎます。${MAX_FILE_SIZE_BYTES / 1024}KB以下のファイルをアップロードしてください。`);
            logoFileInput.value = ''; // ファイル選択をクリア
            uploadButton.disabled = true;
            return;
        }

        // MIMEタイプチェック（画像ファイルのみ許可）は残しておく方が安全だよ
        if (!file.type.startsWith('image/')) {
            alert('画像ファイルを選択してください。');
            logoFileInput.value = '';
            uploadButton.disabled = true;
            return;
        }

        const reader = new FileReader();

        reader.onload = function(e) {
            // 画像サイズのピクセルチェックは不要になったため省略
            // プレビュー表示
            currentLogoPreview.src = e.target.result;
            // BASE64エンコードされたデータをhiddenフィールドにセット
            logoBase64Input.value = e.target.result;
            uploadButton.disabled = false; // アップロードボタンを有効化
        };

        reader.onerror = function() {
            alert('ファイルの読み込みに失敗しました。');
            logoFileInput.value = '';
            uploadButton.disabled = true;
            logoBase64Input.value = '';
            currentLogoPreview.src = currentLogoPreview.dataset.originalSrc || currentLogoPreview.src;
        };

        reader.readAsDataURL(file); // ファイルをData URL (BASE64) として読み込む
    });

    // フォーム送信時の処理 (JavaScriptでバリデーション済みだが、念のため)
    logoUploadForm.addEventListener('submit', function(event) {
        if (!logoBase64Input.value) {
            event.preventDefault(); // 送信をキャンセル
            alert('ロゴ画像が選択されていないか、処理に失敗しました。');
        }
        uploadButton.disabled = true; // 二重送信防止
        uploadButton.textContent = 'アップロード中...';
    });

    // 初期表示時のロゴのsrcをデータ属性に保存（ファイル選択キャンセル時に戻せるように）
    currentLogoPreview.dataset.originalSrc = currentLogoPreview.src;
});