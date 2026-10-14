# FlutterThyro

Haşimato için kişisel diyet ve ilaç takip uygulaması (Android).

- **İndir:** [FlutterThyro.apk (son sürüm)](https://github.com/Backpacker-dotcom/tiroid-defterim/releases/latest/download/FlutterThyro.apk)
- Yiyecek araması: yiyebilirsin / dikkatli ye / uzak dur
- Günlük kalori ve protein, yağ, karbonhidrat takibi; aşım ertesi günün hedefinden düşülür
- Gün gün, ay ay geçmiş
- Her sabah ilaç bildirimi ("Aldım" düğmesiyle)

## Nasıl güncellenir

- `web/index.html` uygulamanın kendisidir. Bu dosya değişince telefondaki uygulama bir sonraki açılışta yeni sürümü indirir; yeniden kurmaya gerek yoktur.
- `android/` klasörü değişince GitHub Actions yeni bir APK derleyip **Releases** bölümüne koyar.

Kayıtlar (yedikleri, ilaç günleri) sadece telefonda saklanır, bu depoya hiçbir kişisel bilgi gönderilmez.

> Bilgiler genel Haşimato beslenme önerilerine dayanır; doktorunun ve diyetisyeninin söyledikleri önce gelir.
