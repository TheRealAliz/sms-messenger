# پیام‌رسان پیامک برای اندروید

[![Android CI](https://github.com/TheRealAliz/sms-messenger/actions/workflows/build.yml/badge.svg)](https://github.com/TheRealAliz/sms-messenger/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/TheRealAliz/sms-messenger?include_prereleases&sort=semver)](https://github.com/TheRealAliz/sms-messenger/releases)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

یک برنامهٔ پیامک ساده و حریم‌خصوصی‌محور برای اندروید، نوشته‌شده با Kotlin و Jetpack Compose.
بدون تبلیغات، بدون حساب کاربری، بدون تحلیل رفتار و بدون ارسال داده به سرور.

> نسخهٔ انگلیسی کامل در [README.md](README.md) قرار دارد.

## امکانات

- ارسال و دریافت پیامک، پیام‌های چندبخشی، گزارش تحویل و پشتیبانی از دو سیم‌کارت
- تشخیص کد یک‌بارمصرف و دکمهٔ کپی سریع (با نادیده گرفتن تاریخ و کدهای USSD)
- پاسخ‌های سریع قابل ویرایش
- سنجاق، بایگانی، مسدودسازی، گزارش هرزنامه، بی‌صدا کردن و ستاره‌دار کردن پیام‌ها
- سطل زباله با پاک‌سازی خودکار قابل تنظیم
- قفل گفتگو و قفل کل برنامه با اثر انگشت یا چهره و رمز پشتیبان
- پنهان کردن متن پیام در اعلان‌ها
- رابط Material 3، حالت روشن و تاریک، رنگ دلخواه، اندازهٔ متن و شکل حباب پیام
- رابط فارسی و انگلیسی با چیدمان راست‌به‌چپ و تقویم جلالی یا میلادی
- پیوندها و کدهای USSD قابل لمس، همراه با پیش‌نمایش پیوند
- همهٔ داده‌ها فقط روی دستگاه ذخیره می‌شوند

## نصب

۱. آخرین فایل `sms-messenger-*.apk` را از بخش
[انتشارها](https://github.com/TheRealAliz/sms-messenger/releases) دانلود کنید.
۲. اجازهٔ نصب از مرورگر یا فایل‌منیجر را تأیید کنید.
۳. برنامه را باز کنید و گزینهٔ **تنظیم به‌عنوان پیش‌فرض** را انتخاب کنید تا پیام‌ها دریافت شوند.
۴. برای نمایش نام و عکس مخاطبان، اجازهٔ دسترسی به مخاطبین را بدهید.

## ساخت از کد

```bash
git clone https://github.com/TheRealAliz/sms-messenger.git
cd sms-messenger
./gradlew assembleDebug
```

خروجی در مسیر `app/build/outputs/apk/debug/app-debug.apk` ساخته می‌شود.
برای ساخت نسخهٔ انتشار، فایل `keystore.properties` را از روی
`keystore.properties.example` بسازید و کلید خودتان را وارد کنید.

## حریم خصوصی

هیچ حساب، تبلیغات، تحلیل رفتار یا گزارش خطایی وجود ندارد. دسترسی به اینترنت فقط
برای دریافت پیش‌نمایش پیوندهایی است که در متن پیام‌ها آمده‌اند. پیام‌ها، مخاطبان و
تنظیمات روی دستگاه می‌مانند و پشتیبان‌گیری اندروید غیرفعال است.

## محدودیت‌ها

- MMS و RCS پشتیبانی نمی‌شوند.
- پیام گروهی پشتیبانی نمی‌شود.
- گزارش تحویل به پشتیبانی اپراتور بستگی دارد.
- پیش‌نمایش پیوند به اینترنت نیاز دارد.

## مشارکت

پیش از ارسال تغییرات، [CONTRIBUTING.md](CONTRIBUTING.md) را بخوانید. برای گزارش
مشکل امنیتی، به‌جای ساخت ایشوی عمومی از [SECURITY.md](SECURITY.md) استفاده کنید.

## مجوز

Copyright 2026 TheRealAliz and contributors.

این پروژه تحت [Apache License 2.0](LICENSE) منتشر شده است. فهرست اجزای
شخص ثالث در [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) آمده است.
