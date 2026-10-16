/**
 * Raksha360 Google Apps Script Mail Gateway
 * Deployed as a Web App (Execute as: Me, Who has access: Anyone)
 *
 * Setup in Google Apps Script Editor (script.google.com):
 * 1. Paste this code into Code.gs
 * 2. Go to Project Settings -> Script Properties -> Add property:
 *    Key: SHARED_SECRET
 *    Value: <your-secure-random-token>
 * 3. Deploy -> New Deployment -> Web App -> Deploy.
 * 4. Copy the Web App URL (ends in /exec) and configure in Render:
 *    GOOGLE_APPS_SCRIPT_URL=https://script.google.com/macros/s/.../exec
 *    GOOGLE_APPS_SCRIPT_SECRET=<your-secure-random-token>
 */

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return createJsonResponse({ status: "error", error: "Missing POST body" }, 400);
    }

    var payload = JSON.parse(e.postData.contents);
    var scriptProps = PropertiesService.getScriptProperties();
    var expectedSecret = scriptProps.getProperty("SHARED_SECRET");

    if (!expectedSecret) {
      return createJsonResponse({ status: "error", error: "SHARED_SECRET property not configured in Google Apps Script" }, 500);
    }

    // 1. Secret Authentication
    var providedSecret = payload.secret || "";
    if (providedSecret !== expectedSecret) {
      return createJsonResponse({ status: "error", error: "Unauthorized: Invalid secret token" }, 401);
    }

    // 2. Replay Protection: Timestamp freshness check (must be within 5 minutes)
    var nowMs = new Date().getTime();
    var reqTimestamp = Number(payload.timestamp || 0);
    if (!reqTimestamp || Math.abs(nowMs - reqTimestamp) > 300000) {
      return createJsonResponse({ status: "error", error: "Expired or invalid timestamp (request rejected for replay protection)" }, 403);
    }

    // 3. Recipient Validation (Strictly one recipient per gateway call)
    var recipient = (payload.recipient || "").trim().toLowerCase();
    if (!recipient || recipient.indexOf("@") === -1 || recipient.indexOf(".") === -1) {
      return createJsonResponse({ status: "error", error: "Invalid recipient email address" }, 400);
    }

    var subject = payload.subject || "Raksha360 Emergency SOS Alert";
    var body = payload.body || "Emergency alert notification from Raksha360.";
    var isDemo = Boolean(payload.isDemo);
    var recipientName = payload.recipientName || "Emergency Contact";

    // 4. Send Email via Gmail / MailApp
    if (isDemo) {
      // In Demo Mode, simulate or clearly label
      MailApp.sendEmail({
        to: recipient,
        subject: "[DEMO TEST] " + subject,
        body: "THIS IS A TEST / SANDBOX ALERT. NO REAL EMERGENCY DISPATCHED.\n\n" + body
      });
    } else {
      MailApp.sendEmail({
        to: recipient,
        subject: subject,
        body: body
      });
    }

    return createJsonResponse({
      status: "success",
      message: "Email delivered successfully via Google Apps Script",
      recipient: recipient,
      sentAt: nowMs,
      nonce: payload.nonce || null
    }, 200);

  } catch (err) {
    return createJsonResponse({
      status: "error",
      error: "Apps Script Exception: " + err.toString()
    }, 500);
  }
}

function doGet(e) {
  return createJsonResponse({
    status: "ok",
    service: "Raksha360 Google Apps Script Mail Gateway",
    time: new Date().toISOString()
  }, 200);
}

function createJsonResponse(data, statusCode) {
  var output = ContentService.createTextOutput(JSON.stringify(data));
  output.setMimeType(ContentService.MimeType.JSON);
  return output;
}
