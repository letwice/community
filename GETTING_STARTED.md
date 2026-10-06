# Getting started with Community Store

This guide gets the Community Store demo running on your own computer. You don't need any technical knowledge: you'll install one free program and then double-click a few files.

It works on **Mac** and **Windows**. The first time takes about 20–30 minutes, mostly waiting for downloads. After that, starting the app takes about a minute.

---

## What you'll need

- A Mac (macOS 13 or newer) or a Windows 10/11 PC
- About **10 GB** of free disk space and at least **8 GB** of memory (RAM)
- An internet connection for the first setup
- **Docker Desktop**, a free program that runs the app. Step 1 explains how to install it.

---

## Step 1 — Install Docker Desktop (one time only)

The app runs inside Docker Desktop.

**If you skip this step,** Setup (Step 2) notices Docker is missing, explains what to do, and can install it for you.

### On a Mac

1. Find out which chip your Mac has: click the Apple menu  → **About This Mac**.
   - "Chip: Apple M1 / M2 / M3 / M4" → download **[Docker Desktop for Apple chip](https://desktop.docker.com/mac/main/arm64/Docker.dmg)**
   - "Processor: Intel" → download **[Docker Desktop for Intel chip](https://desktop.docker.com/mac/main/amd64/Docker.dmg)**
2. Open the downloaded **Docker.dmg** file and drag the **Docker** whale icon into **Applications**.
3. Open **Docker** from your Applications folder. Click **Open** if your Mac asks for confirmation.
4. Accept the terms. You don't need a Docker account: choose **Skip** or **Continue without signing in** if asked.
5. Wait until the whale icon in the menu bar at the top of the screen stops moving. Docker is ready.

### On Windows

1. Download **[Docker Desktop for Windows](https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe)**.
2. Run **Docker Desktop Installer.exe**. Keep the default options, including the "Use WSL 2" tick box.
3. Restart your computer if the installer asks you to.
4. Open **Docker Desktop** from the Start menu and accept the terms. You don't need a Docker account: choose **Skip** or **Continue without signing in**.
5. If Docker shows a message about **WSL** needing an update, click the button it offers (or follow its instructions), then reopen Docker Desktop.
6. Wait until Docker Desktop shows **Engine running** at the bottom left.

---

## Step 2 — Run Setup (one time only)

Open the **community-store-poc** folder and double-click:

| Mac | Windows |
| --- | --- |
| **Setup.command** | **Setup.cmd** |

A window with text appears. Setup:

1. checks that Docker is installed and running, and starts it if it isn't
2. checks that the app has enough memory and free network ports
3. checks for Java (only needed for the optional desktop app, so it's fine if it's missing)
4. downloads and prepares the app. **This takes 5–10 minutes the first time.**

When you see **✔ Setup complete!**, press any key to close the window.

> **Mac says the file "can't be opened"?** Right-click (or Control-click) **Setup.command**, choose **Open**, then **Open** again. You only need to do this once per file.
>
> **Windows shows "Windows protected your PC"?** Click **More info**, then **Run anyway**.

---

## Step 3 — Start the app

Double-click one of these:

| What you want | Mac | Windows |
| --- | --- | --- |
| Start the app (keeps everything you've done before) | **Start.command** | **Start.cmd** |
| Start with a **full set of demo data** (orders, reviews, events, etc.) | **Start with test data.command** | **Start with test data.cmd** |

**"Start with test data"** first **erases everything** in the app and starts fresh, so it asks you to confirm. Type **y** and press **Enter**. It then runs the app's automated checks (108 of them), which fill the app with realistic demo activity. At the end you should see **All automated tests passed**.

When the app is ready, your web browser opens at **http://localhost:9700**. If it doesn't, open your browser and type that address.

### Demo accounts

Every demo account uses the password **Password123!**

| Role | Email | What you can do |
| --- | --- | --- |
| Student | `student@university.com` | Browse, buy (simulated payment), review products and events |
| Vendor | `vendor@community.store` | List items for sale, see your sales dashboard, ship orders |
| Community leader | `leader@community.store` | Post community events |
| Faculty | `faculty@university.com` | Everything a vendor and a community leader can do |
| Admin | `admin@community.store` | Verify new accounts, manage users, release payments, approve events, see all sales |

You can also register your own accounts. Students and faculty must use an email address ending in **@university.com**. No real emails are sent; the verification code is filled in for you.

> **Note:** This is a demo. Payments, bank transfers and identity checks are **simulated**. No real money moves.

---

## Step 4 — Stop the app

When you're done, double-click **Stop.command** (Mac) or **Stop.cmd** (Windows).

Your data is kept for next time. You can also quit Docker Desktop afterwards to free up memory.

---

## Optional — the desktop app

There's also a desktop version for vendors, community leaders and admins. Everything it does is also available in the web app, so you don't need it.

To use it, first install **Java 17**:
- **Mac:** download the **.pkg** installer from [adoptium.net](https://adoptium.net/temurin/releases/?version=17&os=mac).
- **Windows:** download the **.msi** installer from [adoptium.net](https://adoptium.net/temurin/releases/?version=17&os=windows).

Then make sure the app is started and double-click **Open desktop app.command** (Mac) or **Open desktop app.cmd** (Windows). The first time takes a few minutes.

---

## If something goes wrong

| What you see | What to do |
| --- | --- |
| "Docker Desktop is not installed" | Follow Step 1, then run Setup again. |
| "Docker did not start within a few minutes" | Open Docker Desktop yourself. If it's asking you to accept terms, install an update, update WSL or restart, do that, then try again. |
| "These ports are already used by another program" | Another program is using the same connection the app needs. Restart your computer and try again. |
| "Docker only has about 2 GB of memory" | Open Docker Desktop → **Settings** (gear icon) → **Resources** → set **Memory** to 4 GB or more → **Apply & Restart**. |
| "Some parts of the app did not start" | Double-click **Stop**, then **Start** again. Still failing? See below. |
| The web page says it can't connect | The app may still be starting. Wait a minute and refresh the page. Make sure you used **Start** first. |
| The page looks out of date after an update | Press **Ctrl+F5** (Windows) or **Cmd+Shift+R** (Mac) to refresh fully. |
| You want to wipe everything and start over | Double-click **Start with test data** (it resets first). |

**Still stuck?** Ask whoever gave you the app for help, and send them a log file. To create one:
1. Open **Terminal** (Mac: press Cmd+Space, type *Terminal*, press Enter) or **PowerShell** (Windows: Start menu → type *PowerShell*).
2. Drag the **community-store-poc** folder into that window after typing `cd ` (with a space), then press Enter.
3. Type `docker compose logs > community-store-log.txt` and press Enter.
4. Send them the **community-store-log.txt** file that appears in the folder.
