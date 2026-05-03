import re
import time
import unicodedata
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional, Set, Tuple

APP_CATALOG_TTL_SECONDS = 2 * 60 * 60
MAX_APP_CATALOG_SESSIONS = 64
AMBIGUOUS_SCORE_MARGIN = 0.03
MAX_AMBIGUOUS_MATCHES = 5
MAX_SUGGESTED_MATCHES = 5
SUGGESTION_MIN_SCORE = 0.35
MAX_INDEXED_CANDIDATE_APPS = 80
NGRAM_SIZE = 3
MIN_NGRAM_MATCH_RATIO = 0.25

BRAND_ALIAS_GROUPS = [

    # GLOBAL SOCIAL / MESSAGING / GOOGLE / AI
    ("youtube", "you tube", "yt", "يوتيوب", "يوتوب", "يوتيب"),
    ("instagram", "insta", "ig", "انستغرام", "انستقرام", "انستا"),
    ("whatsapp", "whats app", "واتساب", "واتس اب", "واتسآب"),
    ("telegram", "تلجرام", "تليجرام", "تيليجرام"),
    ("tiktok", "tik tok", "تيك توك", "تيكتوك"),
    ("facebook", "fb", "فيسبوك", "فيس بوك"),
    ("messenger", "facebook messenger", "ماسنجر", "مسنجر"),
    ("snapchat", "snap chat", "snap", "سناب شات", "سناب"),
    ("x", "twitter", "تويتر", "اكس", "إكس"),
    ("threads", "ثريدز", "ثريد"),
    ("reddit", "ريديت"),
    ("discord", "ديسكورد"),
    ("linkedin", "linked in", "لينكد ان", "لينكدإن"),
    ("pinterest", "بنترست", "بينترست"),
    ("gmail", "google mail", "جي ميل", "جيميل"),
    ("google chrome", "chrome", "كروم", "جوجل كروم", "غوغل كروم"),
    ("google maps", "maps", "خرائط", "خرائط جوجل", "خرائط غوغل"),
    ("google drive", "drive", "درايف", "جوجل درايف"),
    ("google photos", "photos", "صور جوجل", "جوجل فوتوز"),
    ("google translate", "translate", "ترجمة جوجل", "مترجم جوجل"),
    ("chatgpt", "chat gpt", "gpt", "شات جي بي تي", "تشات جي بي تي", "شات جبت"),
    ("gemini", "google gemini", "جيميني", "جمني"),
    ("copilot", "microsoft copilot", "كوبايلوت", "كو بايلوت"),
    ("zoom", "زووم"),
    ("google meet", "meet", "جوجل ميت", "غوغل ميت"),
    ("skype", "سكايب"),
    ("signal", "سيجنال", "سيغنال"),
    ("viber", "فايبر"),
    ("microsoft teams", "teams", "تيمز", "مايكروسوفت تيمز"),
    ("slack", "سلاك"),
    ("notion", "نوشن", "نوتشن"),
    ("canva", "كانفا"),
    ("capcut", "cap cut", "كاب كت", "كاب كات"),
    ("kinemaster", "kine master", "كين ماستر", "كاين ماستر"),
    ("inshot", "in shot", "ان شوت", "إن شوت"),
    ("picsart", "pics art", "بيكس ارت", "بيكس آرت"),
    ("adobe lightroom", "lightroom", "لايت روم", "لايتروم"),
    ("photoshop express", "adobe photoshop express", "فوتوشوب", "فوتوشوب اكسبريس"),
    ("snapseed", "snap seed", "سناب سيد", "سنابسيد"),
    ("vsco", "فيسكو", "فسكو"),
    ("vn video editor", "vn editor", "vn", "في ان", "في إن"),
    ("filmora", "filmorago", "filmora go", "فيلمورا", "فيلمورا جو"),
    ("powerdirector", "power director", "باور دايركتور"),
    ("spotify", "سبوتيفاي", "سبوتفي"),
    ("netflix", "نتفلكس", "نيتفليكس", "نتفليكس"),
    ("prime video", "amazon prime video", "برايم فيديو"),
    ("disney plus", "disney+", "ديزني بلس"),
    ("twitch", "تويتش"),
    ("soundcloud", "ساوند كلاود"),
    ("shazam", "شازام"),
    ("duolingo", "دوولينجو", "دولينجو"),

    # GLOBAL SHOPPING / FOOD / TRAVEL
    ("amazon", "amazon shopping", "امازون", "أمازون"),
    ("temu", "تيمو", "تيمو شوبينغ"),
    ("shein", "شي ان", "شي إن", "شين"),
    ("aliexpress", "ali express", "علي اكسبريس", "علي إكسبريس"),
    ("ebay", "ايباي", "إيباي"),
    ("etsy", "اتسي", "إتسي"),
    ("uber", "اوبر", "أوبر"),
    ("uber eats", "ubereats", "اوبر ايتس", "أوبر إيتس"),
    ("bolt", "بولت"),
    ("booking", "booking.com", "بوكينج", "بوكنق"),
    ("airbnb", "air bnb", "اير بي ان بي", "إير بي إن بي"),
    ("tripadvisor", "trip advisor", "تريب ادفايزر"),
    ("google wallet", "wallet", "محفظة جوجل"),
    ("apple pay", "ابل باي", "آبل باي"),
    ("paypal", "pay pal", "باي بال"),
    ("revolut", "ريفولوت"),
    ("wise", "transferwise", "وايز"),

    # GLOBAL GAMES
    ("clash of clans", "coc", "كلاش اوف كلانس", "كلاش أوف كلانس", "كلاش كلانس", "كلاش العشائر"),
    ("clash royale", "cr", "كلاش رويال", "كلاش رويالز"),
    ("brawl stars", "براول ستارز", "برول ستارز"),
    ("pubg mobile", "pubg", "ببجي", "بوبجي", "ببجي موبايل"),
    ("call of duty mobile", "cod mobile", "كول اوف ديوتي", "كول أوف ديوتي", "كود موبايل"),
    ("free fire", "فري فاير"),
    ("roblox", "روبلوكس", "روبلكس"),
    ("minecraft", "ماين كرافت", "ماينكرافت"),
    ("fortnite", "فورت نايت", "فورتنايت"),
    ("subway surfers", "صب واي سيرفرز", "سب واي سيرفرز"),
    ("candy crush", "كاندي كراش"),
    ("among us", "امونج اس", "أمونغ آس"),
    ("pokemon go", "بوكيمون جو"),
    ("genshin impact", "جينشن امباكت", "قنشن امباكت"),
    ("league of legends", "lol", "ليج اوف ليجندز"),
    ("wild rift", "وايلد ريفت"),
    ("fifa mobile", "fc mobile", "فيفا موبايل", "اف سي موبايل"),
    ("efootball", "pes", "اي فوتبول", "بيس"),
    ("8 ball pool", "ايت بول بول", "ثمانية بول"),

    # USA / ENGLISH-SPEAKING
    ("chase", "chase bank", "تشيس", "بنك تشيس"),
    ("bank of america", "boa", "بنك اوف امريكا", "بنك أوف أميركا"),
    ("wells fargo", "ويلز فارغو", "ويلز فارجو"),
    ("citibank", "citi", "سيتي بنك", "سيتي"),
    ("capital one", "كابيتال ون"),
    ("us bank", "u.s. bank", "يو اس بنك"),
    ("pnc bank", "pnc", "بي ان سي بنك"),
    ("truist", "تروست", "ترويست"),
    ("american express", "amex", "امريكان اكسبرس", "أمريكان إكسبريس"),
    ("discover", "ديسكفر"),
    ("venmo", "فينمو"),
    ("cash app", "كاش اب", "كاش آب"),
    ("zelle", "زيل", "زيلّي"),
    ("robinhood", "روبن هود"),
    ("coinbase", "كوين بيس"),
    ("walmart", "وول مارت", "والمارت"),
    ("target", "تارغت", "تارجت"),
    ("best buy", "بست باي"),
    ("costco", "كوستكو"),
    ("doordash", "door dash", "دور داش"),
    ("grubhub", "grub hub", "جرب هب"),
    ("instacart", "انستا كارت"),
    ("lyft", "ليفت"),
    ("hulu", "هولو"),
    ("peacock", "بيكوك"),
    ("paramount plus", "paramount+", "باراماونت بلس"),

    # UK
    ("barclays", "باركليز"),
    ("hsbc", "اتش اس بي سي", "إتش إس بي سي"),
    ("lloyds bank", "lloyds", "لويدز بنك"),
    ("natwest", "نات ويست"),
    ("halifax", "هاليفاكس"),
    ("monzo", "مونزو"),
    ("starling bank", "starling", "ستارلينغ بنك"),
    ("tesco", "تيسكو"),
    ("sainsburys", "sainsbury's", "سينزبريز"),
    ("asda", "اسدا", "أسدا"),
    ("deliveroo", "دليفرو"),
    ("just eat", "جست ايت"),
    ("bbc iplayer", "iplayer", "بي بي سي اي بلاير"),

    # CANADA
    ("rbc", "royal bank of canada", "ار بي سي", "رويال بنك اوف كندا"),
    ("td bank", "td canada trust", "تي دي بنك"),
    ("scotiabank", "scotia", "سكوشيا بنك"),
    ("bmo", "bank of montreal", "بي ام او", "بنك مونتريال"),
    ("cibc", "سي اي بي سي"),
    ("interac", "انتراك", "إنترآك"),
    ("tangerine", "تانجرين"),
    ("wealthsimple", "ويلث سمبل"),
    ("tim hortons", "تيم هورتنز"),
    ("skip the dishes", "سكيب ذا ديشز"),

    # TURKEY
    ("ziraat bankasi", "ziraat", "ziraat bank", "زراعت بنك", "بنك زراعت"),
    ("is bankasi", "is bank", "turkiye is bankasi", "iş bankası", "ايش بنك", "بنك ايش"),
    ("garanti bbva", "garanti", "غارانتي", "بنك غارانتي"),
    ("akbank", "اك بنك", "أك بنك"),
    ("yapi kredi", "yapı kredi", "يابي كريدي"),
    ("denizbank", "deniz bank", "دنيز بنك", "دينيز بنك"),
    ("vakifbank", "vakıfbank", "وقف بنك", "فاكيف بنك"),
    ("halkbank", "halk bank", "هالك بنك"),
    ("qnb finansbank", "finansbank", "كي ان بي فينانس بنك"),
    ("teb", "turk ekonomi bankasi", "تي اي بي"),
    ("papara", "بابارا"),
    ("ininal", "اينينال"),
    ("paycell", "بايسل", "باي سيل"),
    ("trendyol", "ترنديول"),
    ("hepsiburada", "hepsi burada", "هبسي بورادا"),
    ("n11", "n eleven", "ان 11", "إن 11"),
    ("getir", "جتير", "غتير"),
    ("yemeksepeti", "yemek sepeti", "يمك سبتي", "يَمَك سَبَتي"),
    ("migros", "ميغروس"),
    ("a101", "a 101", "اي 101"),
    ("bim", "بيم"),
    ("sok market", "şok", "شوك ماركت"),
    ("ciceksepeti", "çiçek sepeti", "تشيشك سبتي"),
    ("sahibinden", "صاحبندن", "صاحيبندن"),
    ("dolap", "دولاب"),
    ("letgo", "let go", "ليت جو"),
    ("pttavm", "ptt avm", "بي تي تي اي في ام"),
    ("turkcell", "ترك سل", "توركسل"),
    ("vodafone", "فودافون"),
    ("turk telekom", "türk telekom", "ترك تيليكوم"),
    ("bein connect", "بي ان كونكت"),
    ("exxen", "اكسن", "إكسن"),
    ("gain", "غين", "غاين"),
    ("blutv", "blue tv", "بلو تي في"),
    ("mhrs", "ام اتش ار اس"),
    ("e devlet", "edevlet", "e-devlet", "اي دولت", "إي دولت"),

    # SAUDI ARABIA
    ("al rajhi bank", "rajhi", "مصرف الراجحي", "الراجحي"),
    ("alinma bank", "بنك الانماء", "الانماء"),
    ("riyad bank", "بنك الرياض", "الرياض بنك"),
    ("saudi national bank", "snb", "البنك الاهلي السعودي", "الاهلي السعودي"),
    ("sabb", "ساب", "البنك السعودي البريطاني"),
    ("albilad bank", "bank albilad", "بنك البلاد", "البلاد"),
    ("stc pay", "اس تي سي باي", "stc باي", "اس تي سي"),
    ("urpay", "ur pay", "يور باي"),
    ("mada pay", "مدى باي"),
    ("absher", "أبشر", "ابشر"),
    ("nafath", "نفاذ"),
    ("tawakkalna", "توكلنا"),
    ("sehhaty", "صحتي"),
    ("najm", "نجم"),
    ("ejar", "إيجار", "ايجار"),
    ("hungerstation", "hunger station", "هنقرستيشن", "هنقر ستيشن"),
    ("jahez", "جاهز"),
    ("mrsool", "مرسول"),
    ("noon", "نون"),
    ("jarir", "jarir bookstore", "جرير", "مكتبة جرير"),
    ("extra", "extra stores", "اكسترا", "إكسترا"),
    ("nahdi", "nahdi pharmacy", "النهدي", "صيدلية النهدي"),
    ("almosafer", "المسافر"),
    ("haraj", "حراج"),

    # UAE
    ("emirates nbd", "بنك الإمارات دبي الوطني", "الإمارات دبي الوطني"),
    ("adcb", "abu dhabi commercial bank", "بنك أبوظبي التجاري", "ابوظبي التجاري"),
    ("fab", "first abu dhabi bank", "بنك أبوظبي الأول", "ابوظبي الاول"),
    ("mashreq", "mashreq bank", "المشرق", "بنك المشرق"),
    ("dubai islamic bank", "dib", "بنك دبي الإسلامي", "دبي الإسلامي"),
    ("careem", "كريم"),
    ("talabat", "طلبات"),
    ("noon uae", "نون الامارات", "نون الإمارات"),
    ("dubizzle", "دوبيزل"),
    ("bayut", "بيوت"),
    ("etisalat", "اتصالات"),
    ("du", "دو"),
    ("botim", "بوتيم"),
    ("payit", "pay it", "باي ات", "باي إت"),
    ("smiles", "سمايلز"),
    ("carrefour", "كارفور"),

    # EGYPT
    ("instapay", "انستا باي", "إنستا باي"),
    ("vodafone cash", "فودافون كاش"),
    ("fawry", "فوري"),
    ("meeza", "ميزة"),
    ("banque misr", "بنك مصر"),
    ("national bank of egypt", "nbe", "البنك الاهلي المصري", "الأهلي المصري"),
    ("cib egypt", "commercial international bank", "سي اي بي", "البنك التجاري الدولي"),
    ("qnb alahli", "qnb الأهلي", "كي يو ان بي الاهلي"),
    ("talabat egypt", "طلبات مصر"),
    ("elmenus", "المنيوز", "المنيوز"),
    ("jumia", "جوميا"),
    ("olx egypt", "olx", "او ال اكس", "أو إل إكس"),
    ("shahid", "شاهد", "شاهد vip", "شاهد في اي بي"),
    ("watch it", "watchit", "واتش ات", "واتش إت"),

    # KUWAIT / QATAR / BAHRAIN / OMAN / JORDAN / IRAQ / LEBANON
    ("kfh", "kuwait finance house", "بيت التمويل الكويتي", "بيتك"),
    ("nbk", "national bank of kuwait", "بنك الكويت الوطني", "الوطني"),
    ("knet", "كي نت"),
    ("carriage", "كاريج"),
    ("snoonu", "سنونو"),
    ("qnb", "qatar national bank", "بنك قطر الوطني"),
    ("ooredoo", "اوريدو", "أوريدو"),
    ("bbk", "bank of bahrain and kuwait", "بنك البحرين والكويت"),
    ("benefitpay", "benefit pay", "بنفت باي"),
    ("bank muscat", "بنك مسقط"),
    ("omantel", "عمانتل"),
    ("zain", "زين"),
    ("orange", "اورنج", "أورنج"),
    ("umniah", "امنية", "أمنية"),
    ("zain cash", "زين كاش"),
    ("jawaker", "جواكر"),
    ("toters", "توترز"),
    ("touch", "تاتش"),
    ("alfa", "الفا", "ألفا"),

    # ARABIC / MENA MEDIA & COMMON
    ("anghami", "انغامي", "أنغامي"),
    ("osn", "او اس ان", "أو إس إن"),
    ("yango", "يانغو", "يانجو"),
    ("imo", "ايمو", "إيمو"),
    ("truecaller", "true caller", "تروكولر", "ترو كولر"),

    ("istanbulkart", "istanbul kart", "İstanbulkart", "istanbul card", "اسطنبول كارت", "إسطنبول كارت", "كرت اسطنبول"),
    ("otobusum nerede", "otobüsüm nerede", "iett otobusum nerede", "iett", "اوتوبوسوم نيرده", "أوتوبوسوم نيرده", "ايتت"),
    ("iett", "i e t t", "iett mobil", "ايتت", "اي اي تي تي"),
    ("ulasim istanbul", "ulaşım istanbul", "metro istanbul", "مترو اسطنبول", "مواصلات اسطنبول"),
    ("moovit", "موفيت", "موڤيت"),
    ("bitaksi", "bi taksi", "بي تاكسي"),
    ("marti", "martı", "مارتي"),
    ("obilet", "o bilet", "او بيليت", "أوبيليت"),
    ("obilet", "otobus bileti", "otobüs bileti", "bus ticket", "تذكرة باص"),

    # ISLAM / QURAN / PRAYER APPS
    ("quran", "koran", "kuran", "kur'an", "qur'an", "القران", "القرآن", "قران", "قرآن", "مصحف"),
    ("holy quran", "the holy quran", "kuran-i kerim", "kuranı kerim", "القران الكريم", "القرآن الكريم", "المصحف الشريف"),
    ("quran majeed", "kuran mecid", "قرآن مجيد", "قران مجيد"),

    # MICROSOFT 365 MOBILE
    ("microsoft word", "word", "وورد", "مايكروسوفت وورد"),
    ("microsoft excel", "excel", "اكسل", "إكسل", "مايكروسوفت اكسل"),
    ("microsoft powerpoint", "powerpoint", "power point", "باوربوينت", "باور بوينت"),
    ("microsoft outlook", "outlook", "اوتلوك", "آوتلوك"),
    ("microsoft onedrive", "onedrive", "one drive", "ون درايف", "وان درايف"),
    ("microsoft onenote", "onenote", "one note", "ون نوت", "وان نوت"),
    (
        "microsoft authenticator",
        "authenticator",
        "authenticator app",
        "autenticator",
        "authentikator",
        "autentikator",
        "otantikator",
        "otentikator",
        "otantikatör",
        "otentikatör",
        "kimlik dogrulayici",
        "kimlik doğrulayıcı",
        "اوثنتيكيتور",
        "المصادق",
    ),

    # DEVELOPER / WORK MOBILE APPS
    ("github", "git hub", "جيت هب", "غيت هب", "جيتهاب"),
    ("gitlab", "git lab", "جيت لاب", "غيت لاب"),
    ("camscanner", "cam scanner", "كام سكانر"),

    # TURKEY EXTRA POPULAR SHOPPING / MARKET
    ("lc waikiki", "lcw", "ال سي وايكيكي"),
    ("defacto", "de facto", "ديفاكتو"),
    ("koton", "كوتون"),
    ("mavi", "مافي"),
    ("flo", "فلو"),
    ("gratis", "غراتيس"),

    # CRYPTO APPS
    ("binance", "بينانس", "باينانس"),
    ("coinbase", "كوين بيس", "كوينبيس"),
    ("kucoin", "كو كوين", "كوكوين"),
    ("kraken", "كراكن"),
    ("okx", "او كي اكس", "اوكي اكس"),
    ("bybit", "باي بيت", "بايبت"),
    ("gate io", "gateio", "غيت اي او"),
    ("crypto com", "crypto.com", "كريبتو كوم"),
    ("trust wallet", "trustwallet", "تراست والت", "محفظة تراست"),
    ("metamask", "meta mask", "ميتا ماسك"),
    ("blockchain", "blockchain wallet", "بلوكتشين", "محفظة بلوكتشين"),
    ("exodus", "اكسودس"),
    ("phantom", "فانتوم"),
    ("bitpanda", "بيت باندا"),
    ("rain", "رين", "rain crypto"),
    ("bitlo", "بيتلو"),
    ("paribu", "بارibu", "باربو"),
    ("btcturk", "btc turk", "بي تي سي تورك"),
    ("nexo", "نيكسو"),
    ("ledger live", "ledger", "ليدجر"),
    ("safe pal", "safepal", "سيف بال"),

    # VPN APPS
    ("turbo vpn", "توربو vpn", "توربو في بي ان"),
    ("vpn proxy master", "proxy master", "في بي ان بروكسي ماستر"),
    ("super vpn", "سوبر vpn", "سوبر في بي ان"),
    ("secure vpn", "سيكيور vpn", "سيكيور في بي ان"),
    ("expressvpn", "express vpn", "اكسبريس vpn", "اكسبريس في بي ان"),
    ("nordvpn", "nord vpn", "نورد vpn", "نورد في بي ان"),
    ("surfshark", "سيرف شارك", "سيرفشارك"),
    ("hotspot shield", "hotspot", "هوت سبوت شيلد"),
    ("proton vpn", "protonvpn", "بروتون vpn"),
    ("windscribe", "ويندسكرايب"),
    ("tunnelbear", "tunnel bear", "تانل بير"),
    ("hola vpn", "hola", "هولا vpn"),
    ("betternet", "better net", "بيتر نت"),
    ("psiphon", "سايفون", "psiphon vpn"),
    ("1.1.1.1", "warp", "cloudflare vpn", "وارب", "كلاودفلير vpn"),
    ("bingx", "bing x", "بينغ اكس", "بينج اكس", "بينغكس", "بينجكس"),
    ("bip", "bi p", "bip uygulamasi", "bip app", "بيپ", "بيب", "بي بي"),
]

BRAND_ALIAS_REPLACEMENTS: List[Tuple[str, str]] = []
for group in BRAND_ALIAS_GROUPS:
    canonical = _canonical = group[0]
    for alias in group:
        BRAND_ALIAS_REPLACEMENTS.append((alias, _canonical))
_normalized_brand_alias_replacements: Optional[List[Tuple[str, str]]] = None
_brand_alias_replacement_index: Optional[Dict[str, List[Tuple[str, str]]]] = None
_brand_alias_compact_replacement_index: Optional[Dict[str, List[Tuple[str, str]]]] = None


@dataclass(frozen=True)
class AppCatalogEntryRecord:
    label: str
    package_name: str
    aliases: List[str]
    match_aliases: List[str]


@dataclass(frozen=True)
class AppMatch:
    label: str
    package_name: str
    score: float


@dataclass(frozen=True)
class AppMatchResolution:
    match: Optional[AppMatch]
    ambiguous_matches: List[AppMatch]
    suggested_matches: List[AppMatch]

    @property
    def is_ambiguous(self) -> bool:
        return bool(self.ambiguous_matches)


_catalogs: Dict[str, Dict[str, object]] = {}


def save_app_catalog(session_id: str, catalog_version: Optional[str], apps: Iterable[object]) -> Dict[str, object]:
    _cleanup_expired_catalogs()

    entries: List[AppCatalogEntryRecord] = []

    for app in apps:
        label = _get_value(app, "label")
        package_name = _get_value(app, "package_name")
        aliases = _get_value(app, "aliases") or []

        if not _has_text(label) or not _has_text(package_name):
            continue

        label_text = str(label).strip()
        package_text = str(package_name).strip()
        alias_texts = [str(alias).strip() for alias in aliases if _has_text(alias)]

        entries.append(AppCatalogEntryRecord(
            label=label_text,
            package_name=package_text,
            aliases=alias_texts,
            match_aliases=_build_match_aliases(label_text, package_text, alias_texts),
        ))

    version = catalog_version or _build_catalog_version(entries)
    now = time.monotonic()
    _catalogs[session_id] = {
        "catalog_version": version,
        "apps": entries,
        "search_index": _build_catalog_search_index(entries),
        "created_at": now,
        "last_seen": now,
    }
    _prune_oldest_catalogs()

    return {
        "session_id": session_id,
        "catalog_version": version,
        "app_count": len(entries),
    }


def has_app_catalog(session_id: Optional[str]) -> bool:
    return _get_catalog(session_id) is not None


def is_catalog_version_current(session_id: Optional[str], catalog_version: Optional[str]) -> bool:
    if not _has_text(catalog_version):
        return True

    if not _has_text(session_id):
        return False

    catalog = _get_catalog(session_id)
    if not catalog:
        return False

    return catalog.get("catalog_version") == catalog_version


def delete_app_catalog(session_id: Optional[str]) -> bool:
    if not _has_text(session_id):
        return False

    return _catalogs.pop(str(session_id), None) is not None


def catalog_count() -> int:
    _cleanup_expired_catalogs()
    return len(_catalogs)


def resolve_app_match(session_id: Optional[str], candidate: str) -> AppMatchResolution:
    if not _has_text(session_id) or not _has_text(candidate):
        return AppMatchResolution(None, [], [])

    catalog = _get_catalog(session_id)
    if not catalog:
        return AppMatchResolution(None, [], [])

    candidate_variants = _expand_text_variants(candidate)
    if not candidate_variants:
        return AppMatchResolution(None, [], [])

    threshold = min(_minimum_score(compact) for _, compact in candidate_variants)
    matches: List[AppMatch] = []
    suggested_matches: List[AppMatch] = []

    candidate_entry_indexes = _candidate_entry_indexes(catalog, candidate_variants)
    if not candidate_entry_indexes:
        return AppMatchResolution(None, [], [])

    entries = catalog["apps"]
    for app_index in candidate_entry_indexes:
        app = entries[app_index]
        score = _score_candidate(candidate_variants, app)
        app_match = AppMatch(app.label, app.package_name, score)
        if score >= threshold:
            matches.append(app_match)
        elif score >= SUGGESTION_MIN_SCORE:
            suggested_matches.append(app_match)

    suggested_matches = _top_matches(suggested_matches, MAX_SUGGESTED_MATCHES)

    if not matches:
        return AppMatchResolution(None, [], suggested_matches)

    matches = _top_matches(matches, len(matches))

    best_match = matches[0]
    ambiguous_matches = [
        match
        for match in matches
        if best_match.score - match.score <= AMBIGUOUS_SCORE_MARGIN
    ]

    if len(ambiguous_matches) > 1:
        return AppMatchResolution(None, ambiguous_matches[:MAX_AMBIGUOUS_MATCHES], suggested_matches)

    return AppMatchResolution(best_match, [], suggested_matches)


def suggest_app_matches(session_id: Optional[str], candidate: str) -> List[AppMatch]:
    return resolve_app_match(session_id, candidate).suggested_matches


def find_app_match(session_id: Optional[str], candidate: str) -> Optional[AppMatch]:
    return resolve_app_match(session_id, candidate).match


def _top_matches(matches: List[AppMatch], limit: int) -> List[AppMatch]:
    if not matches:
        return []

    matches = _dedupe_matches(matches)
    matches.sort(key=lambda match: (-match.score, match.label.casefold(), match.package_name))
    return matches[:limit]


def _build_catalog_search_index(entries: List[AppCatalogEntryRecord]) -> Dict[str, Dict[str, Set[int]]]:
    exact_index: Dict[str, Set[int]] = {}
    compact_index: Dict[str, Set[int]] = {}
    token_index: Dict[str, Set[int]] = {}
    ngram_index: Dict[str, Set[int]] = {}

    for app_index, app in enumerate(entries):
        for alias in app.match_aliases:
            normalized = _normalize_words(alias)
            compact = normalized.replace(" ", "")
            if not compact:
                continue

            _add_index_value(exact_index, normalized, app_index)
            _add_index_value(compact_index, compact, app_index)

            for token in _fast_tokens(normalized):
                if len(token) >= 2:
                    _add_index_value(token_index, token, app_index)

            for ngram in _ngrams(compact):
                _add_index_value(ngram_index, ngram, app_index)

    return {
        "exact": exact_index,
        "compact": compact_index,
        "token": token_index,
        "ngram": ngram_index,
    }


def _candidate_entry_indexes(
    catalog: Dict[str, object],
    candidate_variants: List[Tuple[str, str]],
) -> List[int]:
    search_index = catalog.get("search_index")
    if not isinstance(search_index, dict):
        return []

    exact_index = search_index.get("exact", {})
    compact_index = search_index.get("compact", {})
    token_index = search_index.get("token", {})
    ngram_index = search_index.get("ngram", {})
    candidate_scores: Dict[int, float] = {}

    for candidate_normalized, candidate_compact in candidate_variants:
        if not candidate_compact:
            continue

        _add_candidate_scores(candidate_scores, exact_index.get(candidate_normalized, set()), 6.0)
        _add_candidate_scores(candidate_scores, compact_index.get(candidate_compact, set()), 5.5)

        for token in _fast_tokens(candidate_normalized):
            if len(token) >= 2:
                _add_candidate_scores(candidate_scores, token_index.get(token, set()), 2.0)

        ngrams = _ngrams(candidate_compact)
        if ngrams:
            ngram_hits: Dict[int, int] = {}
            for ngram in ngrams:
                for app_index in ngram_index.get(ngram, set()):
                    ngram_hits[app_index] = ngram_hits.get(app_index, 0) + 1

            min_hits = max(1, int(len(ngrams) * MIN_NGRAM_MATCH_RATIO))
            for app_index, hit_count in ngram_hits.items():
                if hit_count >= min_hits:
                    _add_candidate_scores(candidate_scores, [app_index], hit_count / len(ngrams))

    return [
        app_index
        for app_index, _ in sorted(candidate_scores.items(), key=lambda item: (-item[1], item[0]))
    ][:MAX_INDEXED_CANDIDATE_APPS]


def _add_index_value(index: Dict[str, Set[int]], key: str, app_index: int) -> None:
    if key:
        index.setdefault(key, set()).add(app_index)


def _add_candidate_scores(candidate_scores: Dict[int, float], app_indexes: Iterable[int], score: float) -> None:
    for app_index in app_indexes:
        candidate_scores[app_index] = candidate_scores.get(app_index, 0.0) + score


def _build_match_aliases(label: str, package_name: str, aliases: List[str]) -> List[str]:
    raw_aliases: Set[str] = set()

    for raw_alias in [label, *aliases]:
        if _has_text(raw_alias):
            raw_aliases.add(str(raw_alias))
            raw_aliases.add(_split_compound_words(str(raw_alias)))

    raw_aliases.update(_package_aliases(package_name))

    expanded: Set[str] = set()
    for raw_alias in raw_aliases:
        for normalized, _ in _expand_text_variants(raw_alias):
            if normalized:
                expanded.add(normalized)
                expanded.add(normalized.replace(" ", ""))

    return sorted(expanded)


def _package_aliases(package_name: str) -> Set[str]:
    aliases: Set[str] = set()
    normalized_package = _normalize_words(str(package_name).replace(".", " "))
    tokens = [token for token in normalized_package.split() if token and token not in _package_stopwords()]

    aliases.add(normalized_package)
    if tokens:
        aliases.add(" ".join(tokens))

    for token in tokens:
        aliases.add(token)

    if tokens:
        aliases.add(tokens[-1])

    return aliases


def _package_stopwords() -> Set[str]:
    return {
        "com",
        "org",
        "net",
        "android",
        "app",
        "apps",
        "mobile",
        "client",
        "google",
        "microsoft",
    }


def _score_candidate(candidate_variants: List[Tuple[str, str]], app: AppCatalogEntryRecord) -> float:
    score = 0.0
    candidate_data = [
        (candidate_normalized, candidate_compact, _fast_tokens(candidate_normalized))
        for candidate_normalized, candidate_compact in candidate_variants
        if candidate_compact
    ]

    for candidate_normalized, candidate_compact, candidate_tokens in candidate_data:
        for alias_normalized in app.match_aliases:
            alias_compact = alias_normalized.replace(" ", "")
            if not alias_compact:
                continue
            alias_tokens = _fast_tokens(alias_normalized)

            if candidate_compact == alias_compact:
                score = max(score, 1.0)

            if candidate_normalized and candidate_normalized in alias_tokens:
                score = max(score, 0.96)

            if alias_compact.startswith(candidate_compact) or candidate_compact.startswith(alias_compact):
                score = max(score, 0.90 - _length_penalty(candidate_compact, alias_compact))

            if candidate_compact in alias_compact or alias_compact in candidate_compact:
                score = max(score, 0.84 - _length_penalty(candidate_compact, alias_compact))

            ngram_overlap = 0.0
            if candidate_compact[0] != alias_compact[0]:
                ngram_overlap = _ngram_overlap_ratio(candidate_compact, alias_compact)
                score = max(score, ngram_overlap * 0.82)

            if _should_compare_edit_distance(candidate_compact, alias_compact, ngram_overlap):
                score = max(score, _levenshtein_similarity(candidate_compact, alias_compact))
            score = max(score, _token_overlap_from_sets(candidate_tokens, alias_tokens) * 0.92)

    return score


def _expand_text_variants(value: str) -> List[Tuple[str, str]]:
    variants: Set[str] = set()

    for raw_value in [str(value), _split_compound_words(str(value))]:
        normalized = _normalize_words(raw_value)
        if not normalized:
            continue

        variants.add(normalized)
        variants.add(normalized.replace(" ", ""))
        variants.update(_expand_spelled_text_variants(normalized))

        transliterated = _arabic_to_latin(normalized)
        if transliterated != normalized:
            variants.add(_normalize_words(transliterated))
            variants.update(_expand_spelled_text_variants(transliterated))

        for expanded in _brand_expanded_texts(normalized):
            variants.add(expanded)
            variants.add(expanded.replace(" ", ""))
            variants.update(_expand_spelled_text_variants(expanded))

        for expanded in _brand_expanded_texts(transliterated):
            variants.add(expanded)
            variants.add(expanded.replace(" ", ""))
            variants.update(_expand_spelled_text_variants(expanded))

    return [
        (variant, variant.replace(" ", ""))
        for variant in sorted(variants)
        if variant.replace(" ", "")
    ]


def _brand_expanded_texts(text: str) -> Set[str]:
    variants = {_normalize_words(text)}

    for _ in range(3):
        for variant in list(variants):
            for alias_normalized, canonical_normalized in _brand_alias_replacements_for(variant):
                replaced = _replace_alias_phrase(variant, alias_normalized, canonical_normalized)
                if replaced != variant:
                    variants.add(replaced)

    return {variant for variant in variants if variant}


def _expand_spelled_text_variants(text: str) -> Set[str]:
    tokens = [token for token in _normalize_words(text).split(" ") if token]
    if len(tokens) < 2:
        return set()

    variants = [""]
    matched_tokens = 0
    index = 0

    while index < len(tokens):
        letters, consumed = _spelled_token_to_letters(tokens, index)
        if not letters:
            index += 1
            continue

        next_variants = []
        for variant in variants:
            for letter in letters:
                next_variants.append(variant + letter)

        variants = next_variants[:8]
        matched_tokens += consumed
        index += consumed

    if not variants or matched_tokens < max(2, len(tokens) - 1):
        return set()

    return {variant for variant in variants if variant}


def _spelled_token_to_letters(tokens: List[str], index: int) -> Tuple[List[str], int]:
    token = tokens[index]

    if token in {"duble", "double", "cift", "çift"} and index + 1 < len(tokens):
        next_token = tokens[index + 1]
        if next_token in {"ve", "v", "u", "yu", "you"}:
            return ["w"], 2

    if token in {"dabilyu", "dabiliyu", "dabulyu", "doubleyou", "doubleu"}:
        return ["w"], 1

    if len(token) == 1 and token.isalnum():
        return [token], 1

    letter = _spelled_letter_name_to_letter(token)
    if letter:
        return letter, 1

    return [], 1


def _spelled_letter_name_to_letter(token: str) -> List[str]:
    mapping = {
        "a": ["a"],
        "ah": ["a"],
        "b": ["b"],
        "be": ["b"],
        "bee": ["b"],
        "c": ["c"],
        "ce": ["c"],
        "cee": ["c"],
        "d": ["d"],
        "de": ["d"],
        "dee": ["d"],
        "e": ["e"],
        "f": ["f"],
        "fe": ["f"],
        "ef": ["f"],
        "g": ["g"],
        "ge": ["g"],
        "h": ["h"],
        "he": ["h"],
        "aitch": ["h"],
        "i": ["i"],
        "ii": ["i"],
        "j": ["j"],
        "je": ["j"],
        "jay": ["j"],
        "k": ["k"],
        "ka": ["k"],
        "key": ["k"],
        "l": ["l"],
        "le": ["l"],
        "el": ["l"],
        "m": ["m"],
        "me": ["m"],
        "em": ["m"],
        "n": ["n"],
        "ne": ["n"],
        "en": ["n"],
        "o": ["o"],
        "oh": ["o"],
        "p": ["p"],
        "pe": ["p"],
        "pee": ["p"],
        "q": ["q"],
        "ku": ["q"],
        "queue": ["q"],
        "r": ["r"],
        "re": ["r"],
        "ar": ["r"],
        "s": ["s"],
        "se": ["s"],
        "es": ["s"],
        "t": ["t"],
        "te": ["t"],
        "tee": ["t"],
        "u": ["u"],
        "yu": ["u"],
        "you": ["u"],
        "v": ["v"],
        "ve": ["v", "w"],
        "vee": ["v"],
        "w": ["w"],
        "we": ["w"],
        "x": ["x"],
        "iks": ["x"],
        "ex": ["x"],
        "y": ["y"],
        "ye": ["y"],
        "why": ["y"],
        "z": ["z"],
        "ze": ["z"],
        "zet": ["z"],
        "zed": ["z"],
        "zee": ["z"],
    }
    return mapping.get(token, [])


def _brand_alias_replacements() -> List[Tuple[str, str]]:
    global _normalized_brand_alias_replacements

    if _normalized_brand_alias_replacements is None:
        replacements = set()
        for alias, canonical in BRAND_ALIAS_REPLACEMENTS:
            alias_normalized = _normalize_words(alias)
            canonical_normalized = _normalize_words(canonical)
            if alias_normalized and canonical_normalized:
                replacements.add((alias_normalized, canonical_normalized))

        _normalized_brand_alias_replacements = sorted(
            replacements,
            key=lambda item: len(item[0]),
            reverse=True,
        )

    return _normalized_brand_alias_replacements


def _brand_alias_replacements_for(text: str) -> List[Tuple[str, str]]:
    token_index, compact_index = _brand_alias_indexes()
    candidates: List[Tuple[str, str]] = []
    seen = set()

    for token in _fast_tokens(text):
        for replacement in token_index.get(token, []):
            if replacement not in seen:
                candidates.append(replacement)
                seen.add(replacement)

    for replacement in compact_index.get(text.replace(" ", ""), []):
        if replacement not in seen:
            candidates.append(replacement)
            seen.add(replacement)

    candidates.sort(key=lambda item: len(item[0]), reverse=True)
    return candidates


def _brand_alias_indexes() -> Tuple[Dict[str, List[Tuple[str, str]]], Dict[str, List[Tuple[str, str]]]]:
    global _brand_alias_replacement_index, _brand_alias_compact_replacement_index

    if _brand_alias_replacement_index is None or _brand_alias_compact_replacement_index is None:
        token_index: Dict[str, List[Tuple[str, str]]] = {}
        compact_index: Dict[str, List[Tuple[str, str]]] = {}

        for replacement in _brand_alias_replacements():
            alias_normalized, _ = replacement
            for token in _fast_tokens(alias_normalized):
                token_index.setdefault(token, []).append(replacement)
            compact_index.setdefault(alias_normalized.replace(" ", ""), []).append(replacement)

        _brand_alias_replacement_index = token_index
        _brand_alias_compact_replacement_index = compact_index

    return _brand_alias_replacement_index, _brand_alias_compact_replacement_index


def _replace_alias_phrase(text: str, alias: str, replacement: str) -> str:
    if text == replacement:
        return text

    if text == alias:
        return replacement

    pattern = rf"(?<!\w){re.escape(alias)}(?!\w)"
    replaced = re.sub(pattern, replacement, text, flags=re.IGNORECASE)
    if replaced != text:
        return _normalize_words(replaced)

    text_compact = text.replace(" ", "")
    alias_compact = alias.replace(" ", "")
    if text_compact == alias_compact:
        return replacement

    return text


def _split_compound_words(value: str) -> str:
    text = str(value)
    text = re.sub(r"(?<=[a-z])(?=[A-Z])", " ", text)
    text = re.sub(r"(?<=[A-Za-z])(?=\d)", " ", text)
    text = re.sub(r"(?<=\d)(?=[A-Za-z])", " ", text)
    return text


def _arabic_to_latin(value: str) -> str:
    transliteration = {
        "ا": "a",
        "أ": "a",
        "إ": "i",
        "آ": "a",
        "ب": "b",
        "ت": "t",
        "ث": "th",
        "ج": "j",
        "ح": "h",
        "خ": "kh",
        "د": "d",
        "ذ": "th",
        "ر": "r",
        "ز": "z",
        "س": "s",
        "ش": "sh",
        "ص": "s",
        "ض": "d",
        "ط": "t",
        "ظ": "z",
        "ع": "a",
        "غ": "gh",
        "ف": "f",
        "ق": "q",
        "ك": "k",
        "ل": "l",
        "م": "m",
        "ن": "n",
        "ه": "h",
        "ة": "h",
        "و": "w",
        "ؤ": "w",
        "ي": "y",
        "ى": "a",
        "ئ": "y",
    }
    return _normalize_words("".join(transliteration.get(ch, ch) for ch in str(value)))


def _get_catalog(session_id: Optional[str]) -> Optional[Dict[str, object]]:
    _cleanup_expired_catalogs()
    if not _has_text(session_id):
        return None

    catalog = _catalogs.get(str(session_id))
    if catalog:
        catalog["last_seen"] = time.monotonic()

    return catalog


def _cleanup_expired_catalogs() -> None:
    if not _catalogs:
        return

    now = time.monotonic()
    expired_session_ids = [
        session_id
        for session_id, catalog in _catalogs.items()
        if now - float(catalog.get("last_seen", catalog.get("created_at", now))) > APP_CATALOG_TTL_SECONDS
    ]

    for session_id in expired_session_ids:
        _catalogs.pop(session_id, None)


def _prune_oldest_catalogs() -> None:
    overflow = len(_catalogs) - MAX_APP_CATALOG_SESSIONS
    if overflow <= 0:
        return

    oldest_session_ids = sorted(
        _catalogs,
        key=lambda session_id: float(_catalogs[session_id].get("last_seen", 0.0)),
    )

    for session_id in oldest_session_ids[:overflow]:
        _catalogs.pop(session_id, None)


def _dedupe_matches(matches: List[AppMatch]) -> List[AppMatch]:
    by_package_name: Dict[str, AppMatch] = {}

    for match in matches:
        existing = by_package_name.get(match.package_name)
        if existing is None or match.score > existing.score:
            by_package_name[match.package_name] = match

    return list(by_package_name.values())


def _get_value(obj: object, key: str):
    if isinstance(obj, dict):
        return obj.get(key)
    return getattr(obj, key, None)


def _build_catalog_version(entries: List[AppCatalogEntryRecord]) -> str:
    parts = sorted(f"{entry.package_name}:{entry.label}" for entry in entries)
    return f"{len(entries)}-{abs(hash(tuple(parts))):x}"


def _normalize_words(value: str) -> str:
    text = str(value).casefold().replace("\u0131", "i").replace("\u0130", "i")
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = re.sub(r"[^\w\s]", " ", text, flags=re.UNICODE)
    text = re.sub(r"_+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _tokens(value: str) -> set:
    return {token for token in _normalize_words(value).split(" ") if token}


def _fast_tokens(value: str) -> Set[str]:
    return {token for token in str(value).split(" ") if token}


def _ngrams(value: str, size: int = NGRAM_SIZE) -> Set[str]:
    compact = str(value).replace(" ", "")
    if len(compact) < size:
        return {compact} if compact else set()

    return {compact[index:index + size] for index in range(len(compact) - size + 1)}


def _ngram_overlap_ratio(left: str, right: str) -> float:
    left_ngrams = _ngrams(left)
    right_ngrams = _ngrams(right)
    if not left_ngrams or not right_ngrams:
        return 0.0

    return len(left_ngrams.intersection(right_ngrams)) / min(len(left_ngrams), len(right_ngrams))


def _token_overlap(left: str, right: str) -> float:
    left_tokens = _tokens(left)
    right_tokens = _tokens(right)
    if not left_tokens or not right_tokens:
        return 0.0

    return _token_overlap_from_sets(left_tokens, right_tokens)


def _token_overlap_from_sets(left_tokens: Set[str], right_tokens: Set[str]) -> float:
    if not left_tokens or not right_tokens:
        return 0.0

    overlap = len(left_tokens.intersection(right_tokens))
    return (2.0 * overlap) / (len(left_tokens) + len(right_tokens))


def _should_compare_edit_distance(left: str, right: str, ngram_overlap: float) -> bool:
    max_length = max(len(left), len(right))
    if max_length <= 4:
        return True

    if abs(len(left) - len(right)) > max(2, int(max_length * 0.35)):
        return False

    return left[0] == right[0] or ngram_overlap >= MIN_NGRAM_MATCH_RATIO


def _minimum_score(candidate_compact: str) -> float:
    length = len(candidate_compact)
    if length <= 4:
        return 0.96
    if length <= 7:
        return 0.86
    return 0.74


def _length_penalty(left: str, right: str) -> float:
    return min(0.12, abs(len(left) - len(right)) * 0.01)


def _levenshtein_similarity(left: str, right: str) -> float:
    if not left or not right:
        return 0.0
    if left == right:
        return 1.0

    distance = _levenshtein_distance(left, right)
    return 1.0 - (distance / max(len(left), len(right)))


def _levenshtein_distance(left: str, right: str) -> int:
    previous = list(range(len(right) + 1))
    current = [0] * (len(right) + 1)

    for i, left_char in enumerate(left, start=1):
        current[0] = i
        for j, right_char in enumerate(right, start=1):
            cost = 0 if left_char == right_char else 1
            current[j] = min(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + cost,
            )
        previous, current = current, previous

    return previous[len(right)]


def _has_text(value: object) -> bool:
    return value is not None and str(value).strip() != ""
