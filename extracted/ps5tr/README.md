# PS5 Türkçe Altyazı — Android TV prototipi (v0.1)

Bu Android Studio/Kotlin projesi bir **prototip kaynak kodudur**, hazır kurulabilir APK değildir. APK üretmek için Android Studio ve Android SDK ile derlenmesi gerekir.

## Ne yapar?
- Android `MediaProjection` ile kullanıcı onayı verilmiş ekran yakalamayı başlatır.
- ML Kit Text Recognition ile görüntüdeki metni okur.
- Ekranın alt yarısındaki metinleri olası oyun altyazısı olarak filtreler.
- Yeni metni MyMemory demo çeviri API'sine gönderip Türkçe sonucu kayan altyazı katmanında gösterir.

## Sınırlar / önemli notlar
1. PS Remote Play'in Android TV Box/Stick üzerinde resmî olarak kurulabilmesi ve çalışması cihaz/model/Android sürümüne bağlıdır.
2. Bazı uygulamalar ekran yakalama sırasında siyah görüntü üretebilir veya yakalamayı engelleyebilir. Önce bunu cihazda test edin.
3. Android 14+ cihazlarda MediaProjection oturumlarının başlatılması ve foreground service gereklilikleri üretici/OS sürümüne göre sıkı olabilir. Prototip gerçek cihazda derlenip test edilmelidir.
4. Overlay izni kullanıcı tarafından Ayarlar'dan verilmelidir. Bazı TV arayüzleri bu izin ekranını sunmayabilir.
5. MyMemory endpoint'i demo amaçlıdır; hız, kota, kalite ve gizlilik açısından üretim garantisi yoktur. Üretim için API sağlayıcısı seçilmeli, kullanım şartları incelenmeli ve anahtar güvenli yönetilmelidir.
6. Bu sürüm ilk metin algılandığında çevirir; altyazı zamanlaması, konuşmacı takibi, satır birleştirme ve gelişmiş tekrar filtresi henüz basittir.
7. Proje ilk iskelettir; Android Studio'da derleme hataları ve cihaz izin akışı test edilmeden çalışır APK olduğu iddia edilmemelidir.

## Derleme
- Android Studio'da klasörü açın.
- Android SDK 35 ve uyumlu JDK kurulu olsun.
- Gradle senkronizasyonunu yapın.
- Build > Build APK(s) seçin.

## Kullanım testi
1. PS5 Remote Play'i aynı cihazda açıp bağlantıyı doğrulayın.
2. Bu uygulamayı açın; altyazı penceresi iznini verin.
3. Ekran yakalamayı başlatın ve sistem onay penceresini kabul edin.
4. Remote Play'e geçin, oyunun kendi İngilizce altyazılarını açın.
5. Metin yakalanmıyorsa cihazın ekran yakalama davranışı veya Remote Play uyumluluğu engel olabilir.

## Önerilen sonraki geliştirmeler
- TV kumandasıyla konum/boyut/arka plan ayarı.
- Sadece altyazı bölgesini OCR ile tarama.
- Metin sabitleme ve kısa gecikme penceresi.
- Sağlayıcı arayüzü (Google Cloud Translation / DeepL / kendi sunucun).
- Android sürümleri için güncel MediaProjection foreground-service akışını doğrulama.

## Bilgisayar olmadan bulutta APK derleme (GitHub Actions)
Bu repoda `.github/workflows/build-apk.yml` iş akışı bulunur. Kaynak dosyaları GitHub deposuna yüklendikten sonra Actions sekmesinden **Build Android APK** iş akışı `Run workflow` ile çalıştırılabilir. Derleme başarılı olursa çalıştırma sayfasındaki **Artifacts** bölümünden `PS5TurkceAltyazi-debug-apk` indirilebilir. Bu işlem kaynak kodun mutlaka derleneceğini garanti etmez; ilk derlemede hata çıkarsa loga göre düzeltme gerekir.
