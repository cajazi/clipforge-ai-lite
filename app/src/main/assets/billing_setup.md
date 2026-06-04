# Google Play Billing Setup

## 1. Create Products in Google Play Console

Go to: Play Console → Your App → Monetize → Subscriptions

Create TWO subscriptions:

### Monthly Plan
- Product ID: `clipforge_pro_monthly`
- Name: ClipForge AI Pro Monthly
- Price: $4.99/month
- Grace period: 3 days

### Yearly Plan
- Product ID: `clipforge_pro_yearly`
- Name: ClipForge AI Pro Yearly
- Price: $29.99/year
- Grace period: 3 days

## 2. Add License Key

Go to: Play Console → Setup → API access → Copy license key
Add to your app's build config if needed for verification.

## 3. Test Billing

Use Google Play license testers:
Play Console → Setup → License testing → Add your Gmail

Test product IDs work in internal testing track before release.

## 4. Important Notes

- Billing only works on real devices (not emulators)
- App must be uploaded to Play Console (at least internal testing)
- Use the SAME package name: com.clipforge.ai
- Products must be ACTIVE in Play Console to show prices
