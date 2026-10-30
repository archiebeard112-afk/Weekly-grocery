# Thyme Table (Android)

Kotlin + Jetpack Compose. 111 recipes, sides, favorites, store-aisle shopping list, and King Soopers cart.

## Install
1. Install Android Studio. Open this folder (File > Open). Let it sync; it downloads Gradle itself.
2. Plug in your phone (USB debugging on) and press Run. Or Build > Build APK(s) and send the APK to family.

## King Soopers setup
1. Register a free app at developer.kroger.com.
2. Redirect URI: weeklymeals://kroger
3. Enable Product, Location and Cart APIs (scopes product.compact, cart.basic:write, location.compact).
4. In the app: gear icon > paste Client ID, Secret, ZIP > Save and find my store > Sign in.
5. Shop tab > Add items to King Soopers cart > review products > confirm. Checkout in the King Soopers app/site.

Notes: not compiled/tested yet; Kroger may reject a custom-scheme redirect (if so, use an https redirect that forwards to weeklymeals://kroger). Keys and tokens are stored unencrypted on the phone.
