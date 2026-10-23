# Google Play and App Store guide

You do not need to be a developer. Follow the steps in order. Google and Apple change their websites from time to time, so if a button name is slightly different, use the closest match.

**We (the developers) handle the technical work.** You create the two store accounts, confirm you want to proceed, and send us the details at the end of this document.

You need **both**:

| Store | Phones | Account you must create | Yearly / one-time cost |
|---|---|---|---|
| **Google Play** | Android | Google Play Console | One-time **US $25** (about £20) |
| **Apple App Store** | iPhone / iPad | Apple Developer Program | **US $99 per year** (about £80) |

Do **not** skip Apple. The client goal is to publish on **Google Play and the App Store**.

---

## 1. What you should expect (please read once)

### Android (Google Play)
The Android app already exists. After your Play account is ready, we can upload a **private test** and you can install it from Google Play.

### iPhone (App Store)
Apple will **not** accept the current Android app. An iPhone listing needs a **separate iOS app**. Creating the **Apple Developer account now** is still the right first step — Apple company checks often take **days or weeks**. We cannot upload to the App Store until the iPhone app exists.

### Risk (both stores)
The product reads the **Uber Driver offer on screen** and shows a score. Because of that:

- **Google Play** or the **App Store** may refuse the listing, or remove it later
- **Uber** may object, or restrict a driver account

This is a business risk. You do **not** send a legal contract to Google or Apple for this. We only need a short email from you that you have read this and still want both stores.

You can copy this:

> We have read the store accounts guide. We want **I drive smart** on **Google Play and the Apple App Store**. We understand Google, Apple, or Uber may reject or remove the app. Please start with both developer accounts. We will create the Play Console and Apple Developer accounts and invite you.

A reply of **“Yes, go ahead with Google Play and the App Store”** is enough. No solicitor PDF is required for Google or Apple at this stage.

---

## 2. What you need before you start

- A **company Google account** (Gmail or Google Workspace) to own Play
- An **Apple ID** (email) to own the App Store account — a company email is better than a personal one
- A **debit or credit card** for Google’s **$25** and Apple’s **$99 / year**
- **Company legal name** and **address** (as on a business letter)
- For Apple as a **company**: a **D-U-N-S number** (see Step B2). Individuals can enrol without this, but a company should enrol as an **Organisation**
- Access to your website (for example **idrivesmart.co.uk**) for a privacy policy page later
- **14+ people with Gmail** for Play testing (Google often requires a tester group before a new Play account can go public)
- Later, for App Store testing: people with **Apple IDs** (TestFlight)

You do **not** need Android Studio, Xcode, or a Mac to **create the accounts**. A Mac is only needed later, on our side, to build the iPhone app.

---

## 3. Do the work in this order

1. Read section 1 and send us that you want **both stores**
2. **Step A** — Google Play account  
3. **Step B** — Apple Developer account  
4. Invite **us** on both consoles  
5. Send the reply form at the bottom  
6. We start **Google Play private testing** with the existing Android app  
7. Apple: we prepare the iPhone app and then use **TestFlight** (after the iOS app exists)

You can do Step A and Step B in the **same week**. Apple organisation approval is often the slow part, so start Apple **as soon as you can**.

---

## Step A — Google Play (Android)

### A1. Create the Play developer account

1. On a computer, open [https://play.google.com/console](https://play.google.com/console)
2. Sign in with the Google account that should **own** the Android app
3. Choose to create a **developer account**
4. Prefer an **Organisation / company** account if Google offers that, using the company name
5. Enter legal name, address, email, and phone
6. Pay the **US $25** one-time fee
7. Complete **identity verification**. Google may ask for ID and/or company documents. This can take **hours or a few days**

When it is done, you should see the Play Console dashboard (**All apps**, **Users and permissions**, and similar items).

### A2. Invite the developer (Android)

1. In Play Console, open **Users and permissions** (sometimes under **Settings** / the gear icon)
2. Click **Invite new users**
3. Enter the developer Gmail we have given you
4. Give **Admin**, or **Release apps** / **Release to production**
5. Send the invite and tell us

Do **not** share your Google password. An invite is enough.

### A3. What happens next on Play

We will create the app listing in **your** console, upload the Android app, and start **internal testing**. You will get a Play link. Testers join the test and install **I drive smart** from Google Play.

New Play accounts usually **cannot go public on day one**. Typical path:

1. Internal test (small invite list)
2. Closed test (larger group, often **14 days** — the Console shows the exact rules)
3. Production (public), only when you ask

---

## Step B — Apple App Store (iPhone)

### B1. Create or confirm an Apple ID

1. Open [https://appleid.apple.com](https://appleid.apple.com)
2. Use a **company** email if you can
3. Turn on **two-factor authentication** (Apple requires this)

This Apple ID will **own** the App Store listing. Keep the login details safe inside the company.

### B2. If you enrol as a company: get a D-U-N-S number

Apple’s **Organisation** enrolment needs a **D-U-N-S** number (Dun & Bradstreet). It is free for this purpose.

1. Open Apple’s D-U-N-S lookup: [https://developer.apple.com/enroll/duns-lookup/](https://developer.apple.com/enroll/duns-lookup/)
2. Search for your **legal company name** and address
3. If you **already have** a number, note it
4. If you **do not**, follow Apple’s link to **request** one  
   This can take **several days**. Start this immediately.

Enrol as **Organisation** (company), not **Individual**, if I drive smart is a business. Individual enrolment puts the app in a person’s name and is harder to transfer later.

### B3. Enrol in the Apple Developer Program

1. On a computer (or iPhone), open [https://developer.apple.com/programs/enroll/](https://developer.apple.com/programs/enroll/)
2. Sign in with the Apple ID from B1
3. Choose **Enroll**
4. Choose **Organisation** (company) if that applies
5. Enter legal entity name, D-U-N-S, address, and the person who is legally allowed to bind the company (usually a director)
6. Apple may call or email to **verify the company**. Answer them — this is the usual delay
7. Pay **US $99** for one year
8. Wait until the status is **active** (sometimes the same day, sometimes longer)

When enrolment is active, open [https://appstoreconnect.apple.com](https://appstoreconnect.apple.com). That is where the iPhone app listing will live.

### B4. Invite the developer (iPhone)

In **App Store Connect**:

1. Open **Users and Access**
2. Click the **+** button to add a user
3. Enter the developer name and email we have given you
4. Give **Admin** or **App Manager** (so we can create the app and upload builds)
5. Tick **Access to Certificates, Identifiers & Profiles** if you see that option
6. Send the invite and tell us

Do **not** share your Apple ID password. An invite is enough.

Apple may also ask you to agree to **Paid Apps** / tax / banking later if the app will be paid or have in-app purchases. If the app is **free**, you can still complete the basic agreements when Apple shows them.

### B5. What happens next on the App Store

- We **cannot** upload the current Android file to Apple
- After the Apple account is active, we can register the iPhone app, certificates, and (when the iOS app is built) a **TestFlight** test
- TestFlight is Apple’s private test: you invite Apple IDs, they install from the TestFlight app
- Public App Store release is a separate, later step — only when you ask, and only after Apple reviews the iPhone app

Apple review is strict. They may reject an app that reads another app’s screen. That is the same class of risk as Google Play (section 1).

---

## 4. Privacy policy (required by both stores)

Both Google and Apple need a **public https page**. One page can be used for **both**.

**We will draft the text.** You publish it on your website, for example:

`https://idrivesmart.co.uk/privacy`

It must say in simple language:

- the app is for licensed drivers
- it uses the **offer on screen** to calculate a score
- ride details are sent to **your** server
- crash reporting may be used
- how to contact you and how to ask for data to be deleted

You do not need a solicitor to start **private testing**. You may want one to review the page before a **public** launch.

---

## 5. What you should not do

- Do not wait for the iPhone app before creating the **Apple** account — start the account now
- Do not enrol Apple as a private individual if the product is a company app
- Do not create two Play accounts or two Apple accounts “just in case”
- Do not upload an APK or IPA yourself
- Do not share Google or Apple **passwords** with us — use **invites**
- Do not press **Publish / Submit for Review** on a public listing until we agree it is time

---

## 6. Reply form — copy, fill in, send back

```
STORE ACCOUNTS — DETAILS FOR I DRIVE SMART

1. We have read the guide and want BOTH Google Play and the App Store.
   YES / NO:

2. We understand Google, Apple, or Uber may reject or remove the app.
   YES / NO:

GOOGLE PLAY (ANDROID)
3. Play Console account created:
   YES / NO:
   Google account email:

4. Developer invited on Play Console:
   YES / NO:

APPLE APP STORE (IPHONE)
5. Apple ID that will own the account:
   email:

6. Enrolling as Organisation (company) or Individual?

7. D-U-N-S number (if Organisation):
   HAVE IT / REQUESTED / NEED HELP:

8. Apple Developer Program paid and active:
   YES / NOT YET:

9. Developer invited in App Store Connect:
   YES / NO:

LISTING DETAILS (BOTH STORES)
10. Store name (e.g. I drive smart):

11. One-line summary (max 80 characters):

12. Short description (2–5 sentences, for drivers):

13. Support email:

14. Company legal name:

15. Company address:

16. Countries (e.g. United Kingdom only):

17. Privacy policy URL (or “please draft the text for us”):

18. Android tester Gmail addresses (aim for 14+):
    -
    -

19. iPhone tester Apple IDs (emails), if you have them:
    -
    -

20. Any branding notes (logo, colour, words we must not use):
```

---

## 7. What we do after you send this

| You have sent… | We will do next |
|---|---|
| “Yes, both stores” | Confirm and start |
| Play account + invite | Accept access, create the Android listing, upload a **private Play test** |
| Apple account + invite | Accept access, register the iOS app in App Store Connect |
| Reply form + privacy URL | Use your name, description, and policy on **both** listings |
| Apple enrolment still pending | Continue Play testing; finish App Store when Apple activates you |

If a screen is confusing, send a **screenshot**. We will tell you which button to tap.

You do not need to travel for **account setup**. Android testers need an Android phone. iPhone testers will need an iPhone once TestFlight is ready.
