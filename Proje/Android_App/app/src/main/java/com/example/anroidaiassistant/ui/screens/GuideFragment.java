package com.example.anroidaiassistant.ui.screens;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.anroidaiassistant.R;
import com.example.anroidaiassistant.settings.AssistantSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GuideFragment extends Fragment {
    private LinearLayout root;
    private LinearLayout contentContainer;
    private TextView titleView;
    private TextView subtitleView;
    private View summaryCardView;
    private TextView summaryCountView;
    private TextView summaryLabelView;
    private String renderedLanguage;
    private GuideGroup selectedGroup;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_guide, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view.findViewById(R.id.guideRoot);
        contentContainer = view.findViewById(R.id.guideContent);
        titleView = view.findViewById(R.id.guideTitle);
        subtitleView = view.findViewById(R.id.guideSubtitle);
        summaryCardView = view.findViewById(R.id.guideSummaryCard);
        summaryCountView = view.findViewById(R.id.guideSummaryCount);
        summaryLabelView = view.findViewById(R.id.guideSummaryLabel);
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        String language = AssistantSettings.getLanguage(requireContext());
        if (!language.equals(renderedLanguage)) {
            selectedGroup = null;
            render();
        }
    }

    private void render() {
        if (contentContainer == null) {
            return;
        }

        String language = AssistantSettings.getLanguage(requireContext());
        GuideContent content = guideContent(language);
        renderedLanguage = language;

        boolean rtl = AssistantSettings.LANGUAGE_AR.equals(language);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        contentContainer.removeAllViews();

        titleView.setText(content.title);
        titleView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        titleView.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        subtitleView.setText(content.subtitle);
        subtitleView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        subtitleView.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        boolean showingCategories = selectedGroup == null;
        summaryCardView.setVisibility(showingCategories ? View.VISIBLE : View.GONE);
        summaryCountView.setText(String.valueOf(commandCount(content.groups)));
        summaryCountView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        summaryCountView.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        summaryLabelView.setText(content.summaryLabel);
        summaryLabelView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        summaryLabelView.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);

        if (showingCategories) {
            for (int i = 0; i < content.groups.size(); i++) {
                contentContainer.addView(createCategoryCard(content.groups.get(i), i + 1, rtl));
            }
            return;
        }

        contentContainer.addView(createBackNavigation(content, rtl));
        for (GuideCommand command : selectedGroup.commands) {
            contentContainer.addView(createCommandCard(command, rtl));
        }
    }

    private View createCategoryCard(GuideGroup group, int index, boolean rtl) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.permission_card_bg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            selectedGroup = group;
            render();
        });

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        FrameLayout indexBox = new FrameLayout(requireContext());
        indexBox.setBackgroundResource(R.drawable.summary_icon_soft_bg);
        LinearLayout.LayoutParams indexParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        card.addView(indexBox, indexParams);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(categoryIconRes(index));
        icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.app_primary));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(28), dp(28));
        iconParams.gravity = Gravity.CENTER;
        indexBox.addView(icon, iconParams);

        LinearLayout textColumn = new LinearLayout(requireContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMarginStart(dp(14));
        textParams.setMarginEnd(dp(12));
        card.addView(textColumn, textParams);

        TextView title = text(group.title, 19, R.color.app_text_primary, true, rtl);
        textColumn.addView(title);

        TextView description = text(group.description, 14, R.color.app_text_secondary, false, rtl);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = dp(5);
        textColumn.addView(description, descriptionParams);

        ImageView chevron = new ImageView(requireContext());
        chevron.setImageResource(rtl ? R.drawable.ic_chevron_left : R.drawable.ic_chevron_right);
        chevron.setColorFilter(ContextCompat.getColor(requireContext(), R.color.bottom_nav_inactive));
        card.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(22)));

        return card;
    }

    private int categoryIconRes(int index) {
        switch (index) {
            case 1:
                return R.drawable.ic_guide_general;
            case 2:
                return R.drawable.ic_guide_navigation;
            case 3:
                return R.drawable.ic_guide_touch;
            case 4:
                return R.drawable.ic_guide_search_text;
            case 5:
                return R.drawable.ic_guide_device;
            case 6:
                return R.drawable.ic_guide_time_camera;
            case 7:
                return R.drawable.ic_guide_calls_power;
            default:
                return R.drawable.ic_nav_guide;
        }
    }

    private View createBackNavigation(GuideContent content, boolean rtl) {
        LinearLayout back = new LinearLayout(requireContext());
        back.setOrientation(LinearLayout.HORIZONTAL);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(4), 0, dp(4), dp(12));
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> {
            selectedGroup = null;
            render();
        });

        ImageView backIcon = new ImageView(requireContext());
        backIcon.setImageResource(rtl ? R.drawable.ic_chevron_right : R.drawable.ic_chevron_left);
        backIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.app_primary));
        back.addView(backIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView title = text(content.title, 16, R.color.app_primary, true, rtl);
        back.addView(title);
        return back;
    }

    private View createCommandCard(GuideCommand command, boolean rtl) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.permission_card_bg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);
        card.addView(createCommandRow(command, rtl));
        return card;
    }

    private View createCommandRow(GuideCommand command, boolean rtl) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);

        TextView title = text(command.title, 16, R.color.app_text_primary, true, rtl);
        row.addView(title);

        TextView description = text(command.description, 13, R.color.app_text_secondary, false, rtl);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = dp(3);
        row.addView(description, descriptionParams);

        for (String example : command.examples) {
            TextView chip = text(example, 13, R.color.app_primary, true, rtl);
            chip.setBackgroundResource(R.drawable.permission_chip_on_bg);
            chip.setPadding(dp(10), dp(7), dp(10), dp(7));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            chipParams.topMargin = dp(7);
            row.addView(chip, chipParams);
        }

        return row;
    }

    private TextView text(String value, int sp, int colorRes, boolean bold, boolean rtl) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        view.setTextSize(sp);
        view.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        view.setTextDirection(rtl ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private int commandCount(List<GuideGroup> groups) {
        int count = 0;
        for (GuideGroup group : groups) {
            count += group.commands.size();
        }
        return count;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GuideContent guideContent(String language) {
        if (AssistantSettings.LANGUAGE_EN.equals(language)) {
            return englishContent();
        }
        if (AssistantSettings.LANGUAGE_AR.equals(language)) {
            return arabicContent();
        }
        return turkishContent();
    }

    private GuideContent turkishContent() {
        return new GuideContent(
                "Kılavuz",
                "Desteklenen sesli komutlar görevlerine göre gruplanmıştır.",
                "Desteklenen komut",
                groups(
                        group("Genel", "Uygulama, kişi ve temel yönetim komutları.",
                                cmd("Uygulama aç", "Yüklü uygulama adını söyle. Tek eşleşme varsa direkt açılır, birden fazla aday varsa seçtirir.",
                                        "aç <uygulama adı> | Örnek: WhatsApp, Instagram"),
                                cmd("Uygulama bilgisi", "Bir uygulamanın Android uygulama bilgisi ekranını açar.",
                                        "<uygulama adı> uygulama bilgisini aç | Örnek: WhatsApp, Telegram"),
                                cmd("Uygulama kaldır", "Uygulamayı bulur, önce gerekirse aday seçtirir, sonra kaldırma onayı ister.",
                                        "kaldır <uygulama adı> | Örnek: BingX, Porty"),
                                cmd("Kişiyi ara", "Rehberdeki kişiyi arar. Yakın isimlerde veya birden fazla adayda seçim gösterir.",
                                        "ara <kişi adı> | Örnek: Ahmet, Mehmet, Ali")
                        ),
                        group("Gezinme", "Geri, ana ekran, son uygulamalar ve bildirimler.",
                                cmd("Geri git", "Geçerli ekranda geri aksiyonunu çalıştırır.",
                                        "geri git"),
                                cmd("Ana ekrana git", "Telefonun ana ekranına döner.",
                                        "ana ekrana git"),
                                cmd("Uygulamayı kapat", "Açık uygulamadan çıkmak için arka arkaya geri aksiyonlarını kullanır.",
                                        "uygulamayı kapat"),
                                cmd("Son uygulamaları aç", "Android son uygulamalar ekranını açar.",
                                        "son uygulamaları göster"),
                                cmd("Bildirimleri aç", "Bildirim panelini indirir.",
                                        "bildirimleri aç")
                        ),
                        group("Dokunma", "Bas, çift tıkla, uzun bas, kaydır ve sürükle.",
                                cmd("Öğeye bas", "Ekrandaki yazı, ikon veya buton adına göre tıklar. Belirsizlik varsa aday gösterir.",
                                        "bas <öğe adı> | Örnek: arama, üç nokta, kalp"),
                                cmd("Çift tıkla", "Grid açıksa seçilen grid numarasına, değilse ekran merkezine çift tıklar.",
                                        "ekrana çift tıkla", "beşe çift tıkla"),
                                cmd("Basılı tut", "Grid açıksa seçilen grid numarasına, değilse ekran merkezine uzun basar.",
                                        "ekrana basılı tut", "üçe basılı tut"),
                                cmd("Kaydır", "Sayfayı yukarı veya aşağı kaydırır.",
                                        "aşağı kaydır", "yukarı kaydır"),
                                cmd("Sürükle", "Sağa, sola, yukarı veya aşağı swipe hareketi yapar.",
                                        "sola kaydır", "sağa sürükle")
                        ),
                        group("Arama ve Yazı", "Arama, metin yazma, temizleme ve numaralı hedefler.",
                                cmd("Arama yap", "Açık ekrandaki arama alanına sorguyu yazar; gerekirse önce büyüteç ikonunu açar.",
                                        "<arama metni> için arama yap | Örnek: hava durumu, İstanbul hava durumu"),
                                cmd("Metin yaz", "Ekrandaki input alanını bulur ve söylenen metni yazar.",
                                        "yaz <metin> | Örnek: merhaba, toplantıya geç kalacağım"),
                                cmd("Metni temizle", "Odaklanılan veya bulunan input alanındaki metni temizler.",
                                        "metni temizle", "yazıyı sil"),
                                cmd("Numaraları göster", "Ekrandaki tıklanabilir öğeleri numaralandırır; sonra numarayı söyleyerek basabilirsin.",
                                        "numaraları göster", "etiketleri göster"),
                                cmd("Grid göster", "Ekranı bölgelere ayırır. Daha küçük veya büyük grid isteyebilir, numarayla dokunabilirsin.",
                                        "grid göster", "gridi küçült")
                        ),
                        group("Cihaz Kontrolleri", "Wi-Fi, Bluetooth, ses, parlaklık ve hızlı ayarlar.",
                                cmd("Wi-Fi", "Wi-Fi durumunu açar veya kapatır.",
                                        "Wi-Fi aç", "Wi-Fi kapat"),
                                cmd("Bluetooth", "Bluetooth durumunu açar veya kapatır.",
                                        "Bluetooth aç", "Bluetooth kapat"),
                                cmd("Konum", "Konum ayarını açar veya kapatır.",
                                        "konum aç", "konum kapat"),
                                cmd("Mobil veri", "Mobil veriyi açar veya kapatır.",
                                        "mobil veri aç", "mobil veriyi kapat"),
                                cmd("Mobil erişim noktası", "Hotspot durumunu açar veya kapatır.",
                                        "mobil erişim noktası aç", "hotspot kapat"),
                                cmd("Fener", "Telefon fenerini açar veya kapatır.",
                                        "fener aç", "feneri kapat"),
                                cmd("Ses modu", "Telefonu normal, titreşim veya sessiz moda alır.",
                                        "telefonu sessize al", "titreşim moduna al"),
                                cmd("Klavye", "Klavye açma veya kapatma davranışını çalıştırır.",
                                        "klavyeyi aç", "klavyeyi kapat"),
                                cmd("Ses seviyesi", "Ses seviyesini artırır, azaltır, sessize alır veya belirli seviyeye getirir.",
                                        "sesi yükselt", "sesi maksimum yap"),
                                cmd("Parlaklık", "Ekran parlaklığını artırır veya azaltır.",
                                        "parlaklığı artır", "ekranı loş yap")
                        ),
                        group("Zaman ve Kamera", "Alarm, zamanlayıcı, fotoğraf ve ekran görüntüsü.",
                                cmd("Zamanlayıcı kur", "Süreyi saniye, dakika veya saat olarak alır ve zamanlayıcı başlatır.",
                                        "10 dakikalık zamanlayıcı kur", "30 saniyelik zamanlayıcı başlat"),
                                cmd("Alarm kur", "Saat zorunludur; gün, dakika ve sabah/akşam isteğe bağlıdır.",
                                        "saat 7'ye alarm kur", "pazartesi akşam 5'e alarm kur"),
                                cmd("Fotoğraf çek", "Kamerayı açar; ön veya arka kamera belirtilirse önce kamerayı çevirir.",
                                        "fotoğraf çek", "ön kamerayla fotoğraf çek"),
                                cmd("Ekran görüntüsü al", "Android ekran görüntüsü aksiyonunu çalıştırır.",
                                        "ekran görüntüsü al", "screenshot al")
                        ),
                        group("Aramalar ve Güç", "Telefon aramaları, cihaz gücü ve asistan kontrolü.",
                                cmd("Aramayı cevapla", "Gelen aramayı kullanıcı arayüzüyle uğraşmadan cevaplar.",
                                        "aramayı cevapla"),
                                cmd("Aramayı reddet", "Gelen aramayı reddeder veya kapatır.",
                                        "aramayı reddet"),
                                cmd("Telefonu kapat", "Kritik işlem olduğu için önce onay ister, sonra güç menüsünden kapatır.",
                                        "telefonu kapat", "cihazı tamamen kapat"),
                                cmd("Telefonu yeniden başlat", "Kritik işlem olduğu için önce onay ister, sonra yeniden başlatır.",
                                        "telefonu yeniden başlat"),
                                cmd("Dinlemeyi durdur", "Asistan dinlemeyi bırakır. Bu intent sadece güvenli rule komutlarıyla çalışır.",
                                        "dinlemeyi bırak", "asistanı durdur")
                        )
                )
        );
    }

    private GuideContent englishContent() {
        return new GuideContent(
                "Guide",
                "Supported voice commands are grouped by task.",
                "Supported commands",
                groups(
                        group("General", "Apps, contacts, and basic app management.",
                                cmd("Open app", "Say the installed app name. A single match opens directly; multiple candidates ask you to choose.",
                                        "open <app name> | Example: WhatsApp, Instagram"),
                                cmd("App info", "Opens the Android app info screen for an installed app.",
                                        "open app info for <app name> | Example: WhatsApp, Telegram"),
                                cmd("Uninstall app", "Finds the app, asks you to choose if needed, then asks for uninstall confirmation.",
                                        "uninstall <app name> | Example: BingX, Porty"),
                                cmd("Call contact", "Calls a saved contact. Close names or duplicate names are shown as choices.",
                                        "call <contact name> | Example: John, Ahmad, Ali")
                        ),
                        group("Navigation", "Back, home, recents, notifications, and app closing.",
                                cmd("Go back", "Runs the current screen back action.",
                                        "go back"),
                                cmd("Go home", "Returns to the phone home screen.",
                                        "go home"),
                                cmd("Close app", "Leaves the current app by using repeated back actions.",
                                        "close app"),
                                cmd("Recent apps", "Opens the Android recent apps screen.",
                                        "show recent apps"),
                                cmd("Notifications", "Pulls down the notification panel.",
                                        "open notifications")
                        ),
                        group("Touch", "Tap, double tap, hold, scroll, and swipe.",
                                cmd("Tap item", "Taps visible text, icons, or buttons by name. Ambiguous results are shown as choices.",
                                        "tap <item name> | Example: search, three dots, like"),
                                cmd("Double tap", "Double taps the selected grid number when grid is active, otherwise the center of the screen.",
                                        "double tap the screen", "double tap five"),
                                cmd("Hold screen", "Long presses the selected grid number when grid is active, otherwise the center of the screen.",
                                        "long press the screen", "hold number three"),
                                cmd("Scroll", "Scrolls the page up or down.",
                                        "scroll down", "scroll up"),
                                cmd("Swipe", "Performs a left, right, up, or down swipe gesture.",
                                        "swipe left", "swipe right")
                        ),
                        group("Search & Text", "Search, typing, clearing text, and numbered targets.",
                                cmd("Search query", "Writes the query into the current search field; if needed, opens the search icon first.",
                                        "search <query> | Example: weather, Istanbul weather"),
                                cmd("Write text", "Finds an input field and types the requested text.",
                                        "write <text> | Example: hello, I will be late"),
                                cmd("Clear text", "Clears the focused or detected input field.",
                                        "clear text", "delete the text"),
                                cmd("Show numbers", "Labels clickable screen elements with numbers. Then say a number to tap it.",
                                        "show numbers", "show labels"),
                                cmd("Show grid", "Divides the screen into tap zones. You can make it smaller or larger and tap by number.",
                                        "show grid", "make grid smaller")
                        ),
                        group("Device Controls", "Wi-Fi, Bluetooth, sound, brightness, and quick settings.",
                                cmd("Wi-Fi", "Turns Wi-Fi on or off.",
                                        "turn on Wi-Fi", "turn off Wi-Fi"),
                                cmd("Bluetooth", "Turns Bluetooth on or off.",
                                        "turn on Bluetooth", "turn off Bluetooth"),
                                cmd("Location", "Turns location on or off.",
                                        "turn on location", "turn off location"),
                                cmd("Mobile data", "Turns mobile data on or off.",
                                        "turn on mobile data", "turn off mobile data"),
                                cmd("Mobile hotspot", "Turns hotspot on or off.",
                                        "turn on mobile hotspot", "turn off hotspot"),
                                cmd("Flashlight", "Turns the flashlight on or off.",
                                        "turn on flashlight", "turn off flashlight"),
                                cmd("Sound mode", "Sets the phone to normal, vibrate, or silent mode.",
                                        "set phone to silent mode", "set sound mode to vibrate"),
                                cmd("Keyboard", "Runs keyboard open or close behavior.",
                                        "open the keyboard", "close the keyboard"),
                                cmd("Volume", "Raises, lowers, mutes, unmutes, or sets the volume level.",
                                        "increase the volume", "set volume to max"),
                                cmd("Brightness", "Increases or decreases screen brightness.",
                                        "increase brightness", "decrease brightness")
                        ),
                        group("Time & Camera", "Timers, alarms, photos, and screenshots.",
                                cmd("Set timer", "Starts a timer using seconds, minutes, or hours.",
                                        "set a timer for 10 minutes", "start a 30 second timer"),
                                cmd("Set alarm", "Hour is required; day, minute, and am/pm are optional.",
                                        "set an alarm for 7 am", "set an alarm for Monday 5 pm"),
                                cmd("Take photo", "Opens camera and captures. If front or back camera is specified, it switches first.",
                                        "take a photo", "take a selfie"),
                                cmd("Take screenshot", "Runs the Android screenshot action.",
                                        "take a screenshot", "capture the screen")
                        ),
                        group("Calls & Power", "Calls, device power, and assistant control.",
                                cmd("Answer call", "Answers an incoming call without touching the call UI.",
                                        "answer call"),
                                cmd("Reject call", "Rejects or hangs up an incoming call.",
                                        "reject call"),
                                cmd("Power off", "Asks for confirmation first, then opens the power flow to shut down.",
                                        "power off phone", "shut down the phone"),
                                cmd("Restart device", "Asks for confirmation first, then restarts the phone.",
                                        "restart phone", "reboot device"),
                                cmd("Stop listening", "Stops the assistant. This intent is only accepted from safe rule commands.",
                                        "stop listening", "turn off voice assistant")
                        )
                )
        );
    }

    private GuideContent arabicContent() {
        return new GuideContent(
                "الدليل",
                "الأوامر الصوتية المدعومة مقسمة حسب نوع المهمة.",
                "أمر مدعوم",
                groups(
                        group("عام", "التطبيقات، جهات الاتصال، وإدارة التطبيقات.",
                                cmd("فتح تطبيق", "قل اسم التطبيق المثبت. إذا كان هناك تطابق واحد يفتح مباشرة، وإذا وجدت عدة نتائج يتم عرض الاختيار.",
                                        "افتح <اسم التطبيق> | مثال: واتساب، انستغرام"),
                                cmd("معلومات التطبيق", "يفتح صفحة معلومات التطبيق في إعدادات أندرويد.",
                                        "افتح معلومات <اسم التطبيق> | مثال: واتساب، تيليجرام"),
                                cmd("إزالة تطبيق", "يعثر على التطبيق، يعرض الاختيار عند الحاجة، ثم يطلب تأكيد الحذف.",
                                        "احذف <اسم التطبيق> | مثال: سناب شات، تيك توك"),
                                cmd("الاتصال بجهة اتصال", "يتصل باسم محفوظ في جهات الاتصال. الأسماء المتشابهة أو المتكررة تعرض كخيارات.",
                                        "اتصل بـ <اسم جهة الاتصال> | مثال: أحمد، محمد، علي")
                        ),
                        group("التنقل", "الرجوع، الرئيسية، التطبيقات الأخيرة، والإشعارات.",
                                cmd("الرجوع", "ينفذ زر الرجوع في الشاشة الحالية.",
                                        "ارجع"),
                                cmd("الشاشة الرئيسية", "يرجع إلى الشاشة الرئيسية للهاتف.",
                                        "اذهب إلى الشاشة الرئيسية"),
                                cmd("إغلاق التطبيق", "يخرج من التطبيق الحالي باستخدام الرجوع المتكرر.",
                                        "أغلق التطبيق"),
                                cmd("التطبيقات الأخيرة", "يفتح شاشة التطبيقات الأخيرة.",
                                        "أظهر التطبيقات الأخيرة"),
                                cmd("الإشعارات", "يفتح لوحة الإشعارات.",
                                        "افتح الإشعارات")
                        ),
                        group("اللمس", "النقر، النقر المزدوج، الضغط المطول، التمرير والسحب.",
                                cmd("النقر على عنصر", "ينقر على نص أو أيقونة أو زر ظاهر حسب الاسم. إذا كان هناك غموض تظهر الخيارات.",
                                        "اضغط على <اسم العنصر> | مثال: البحث، الثلاث نقاط، القلب"),
                                cmd("النقر مرتين", "إذا كانت الشبكة ظاهرة ينقر مرتين على رقم الشبكة، وإلا ينقر في منتصف الشاشة.",
                                        "اضغط مرتين على الشاشة", "اضغط مرتين على خمسة"),
                                cmd("ضغط مطول", "إذا كانت الشبكة ظاهرة يضغط مطولا على رقم الشبكة، وإلا يضغط مطولا في منتصف الشاشة.",
                                        "اضغط مطولا على الشاشة", "اضغط مطولا على الرقم ثلاثة"),
                                cmd("تمرير", "يمرر الصفحة للأعلى أو للأسفل.",
                                        "مرر للأسفل", "مرر للأعلى"),
                                cmd("سحب", "ينفذ حركة سحب يمينا أو يسارا أو للأعلى أو للأسفل.",
                                        "اسحب لليسار", "اسحب لليمين")
                        ),
                        group("البحث والكتابة", "البحث، الكتابة، مسح النص، والأهداف المرقمة.",
                                cmd("البحث", "يكتب عبارة البحث في حقل البحث الحالي، وإذا لزم الأمر يفتح أيقونة البحث أولا.",
                                        "ابحث عن <عبارة البحث> | مثال: الطقس، طقس إسطنبول"),
                                cmd("كتابة نص", "يعثر على حقل إدخال ويكتب النص المطلوب.",
                                        "اكتب <النص> | مثال: مرحبا، سأتأخر"),
                                cmd("مسح النص", "يمسح النص من حقل الإدخال المحدد أو المكتشف.",
                                        "امسح النص", "احذف الكتابة"),
                                cmd("إظهار الأرقام", "يرقم العناصر القابلة للنقر على الشاشة. بعد ذلك قل الرقم للضغط عليه.",
                                        "اظهر الأرقام", "اعرض التسميات"),
                                cmd("إظهار الشبكة", "يقسم الشاشة إلى مناطق نقر. يمكنك تصغيرها أو تكبيرها ثم الضغط بالرقم.",
                                        "اظهر الشبكة", "صغر الشبكة")
                        ),
                        group("تحكم الجهاز", "واي فاي، بلوتوث، الصوت، السطوع، والإعدادات السريعة.",
                                cmd("واي فاي", "يشغل أو يوقف الواي فاي.",
                                        "شغل الواي فاي", "أوقف الواي فاي"),
                                cmd("بلوتوث", "يشغل أو يوقف البلوتوث.",
                                        "شغل البلوتوث", "أوقف البلوتوث"),
                                cmd("الموقع", "يشغل أو يوقف الموقع.",
                                        "شغل الموقع", "أوقف الموقع"),
                                cmd("بيانات الهاتف", "يشغل أو يوقف بيانات الهاتف.",
                                        "شغل بيانات الهاتف", "أوقف بيانات الهاتف"),
                                cmd("نقطة الاتصال", "يشغل أو يوقف نقطة الاتصال.",
                                        "شغل نقطة الاتصال", "أوقف الهوتسبوت"),
                                cmd("المصباح", "يشغل أو يوقف الفلاش.",
                                        "شغل المصباح", "أوقف المصباح"),
                                cmd("وضع الصوت", "يغير الهاتف إلى الوضع العادي أو الاهتزاز أو الصامت.",
                                        "اجعل الهاتف صامتا", "اجعل الصوت اهتزاز"),
                                cmd("لوحة المفاتيح", "ينفذ فتح أو إغلاق لوحة المفاتيح.",
                                        "افتح لوحة المفاتيح", "أغلق لوحة المفاتيح"),
                                cmd("مستوى الصوت", "يرفع الصوت، يخفضه، يكتمه، أو يضبطه على مستوى محدد.",
                                        "ارفع الصوت", "اجعل الصوت على أعلى مستوى"),
                                cmd("السطوع", "يزيد أو يقلل سطوع الشاشة.",
                                        "زد السطوع", "اخفض إضاءة الشاشة")
                        ),
                        group("الوقت والكاميرا", "المؤقتات، المنبهات، الصور، ولقطات الشاشة.",
                                cmd("ضبط مؤقت", "يشغل مؤقتا بالثواني أو الدقائق أو الساعات.",
                                        "اضبط مؤقتا لمدة 10 دقائق", "ابدأ مؤقتا لمدة 30 ثانية"),
                                cmd("ضبط منبه", "الساعة مطلوبة؛ اليوم والدقيقة وصباحا أو مساء اختيارية.",
                                        "اضبط منبها للساعة 7 صباحا", "اضبط منبها يوم الاثنين الساعة 5 مساء"),
                                cmd("التقاط صورة", "يفتح الكاميرا ويلتقط صورة. إذا ذكرت الأمامية أو الخلفية يغير الكاميرا أولا.",
                                        "التقط صورة", "التقط سيلفي"),
                                cmd("لقطة شاشة", "ينفذ أمر لقطة الشاشة في أندرويد.",
                                        "خذ لقطة شاشة", "صور الشاشة")
                        ),
                        group("المكالمات والطاقة", "المكالمات، طاقة الجهاز، والتحكم بالمساعد.",
                                cmd("الرد على مكالمة", "يرد على المكالمة الواردة دون لمس واجهة الاتصال.",
                                        "أجب على المكالمة"),
                                cmd("رفض مكالمة", "يرفض أو ينهي المكالمة الواردة.",
                                        "ارفض المكالمة"),
                                cmd("إيقاف الهاتف", "يطلب التأكيد أولا، ثم يفتح مسار إيقاف تشغيل الهاتف.",
                                        "أطفئ الهاتف", "أغلق الهاتف"),
                                cmd("إعادة التشغيل", "يطلب التأكيد أولا، ثم يعيد تشغيل الهاتف.",
                                        "أعد تشغيل الهاتف", "إعادة تشغيل الجهاز"),
                                cmd("إيقاف الاستماع", "يوقف المساعد. هذا الأمر يقبل فقط من أوامر rule الآمنة.",
                                        "توقف عن الاستماع", "أوقف المساعد")
                        )
                )
        );
    }

    private List<GuideGroup> groups(GuideGroup... groups) {
        return new ArrayList<>(Arrays.asList(groups));
    }

    private GuideGroup group(String title, String description, GuideCommand... commands) {
        return new GuideGroup(title, description, new ArrayList<>(Arrays.asList(commands)));
    }

    private GuideCommand cmd(String title, String description, String... examples) {
        return new GuideCommand(title, description, new ArrayList<>(Arrays.asList(examples)));
    }

    private static final class GuideContent {
        final String title;
        final String subtitle;
        final String summaryLabel;
        final List<GuideGroup> groups;

        GuideContent(String title, String subtitle, String summaryLabel, List<GuideGroup> groups) {
            this.title = title;
            this.subtitle = subtitle;
            this.summaryLabel = summaryLabel;
            this.groups = groups;
        }
    }

    private static final class GuideGroup {
        final String title;
        final String description;
        final List<GuideCommand> commands;

        GuideGroup(String title, String description, List<GuideCommand> commands) {
            this.title = title;
            this.description = description;
            this.commands = commands;
        }
    }

    private static final class GuideCommand {
        final String title;
        final String description;
        final List<String> examples;

        GuideCommand(String title, String description, List<String> examples) {
            this.title = title;
            this.description = description;
            this.examples = examples;
        }
    }
}
