(ns nextplace.email
  (:require [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [postal.core :as postal]))

(defn send-email
  "Send email via configured provider"
  [{:keys [provider api-key from]} to subject body-html]
  (try
    (let [conn   (case provider
                   :postmark {:host "smtp.postmarkapp.com"
                              :user api-key
                              :pass api-key
                              :port 587
                              :tls  true})
          result (postal/send-message
                  conn
                  {:from    from
                   :to      to
                   :subject subject
                   :body    [{:type    "text/html"
                              :content body-html}]})]
      (if (= :SUCCESS (:error result))
        {:success true}
        (do
          (log/error "Email send failed" {:result result})
          {:success false :error (:message result)})))
    (catch Exception e
      (log/error e "Email send exception")
      {:success false :error (.getMessage e)})))

(defn magic-link-email
  "Generate magic link email HTML"
  [base-url token email]
  (str
   "<html><body style=\"font-family: 'Roboto', sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;\">"
   "<h2 style=\"color: #FF6B35;\">Sign in to Nextplace</h2>"
   "<p>Click the link below to sign in to your account:</p>"
   "<p style=\"margin: 30px 0;\">"
   "<a href=\"" base-url "/auth?token=" token "\" "
   "style=\"background: linear-gradient(135deg, #FF6B35 0%, #FFA726 100%); "
   "color: white; padding: 12px 24px; text-decoration: none; "
   "border-radius: 4px; display: inline-block;\">Sign in to Nextplace</a>"
   "</p>"
   "<p style=\"color: #757575; font-size: 0.875rem;\">"
   "This link expires in 15 minutes.</p>"
   "<p style=\"color: #757575; font-size: 0.875rem;\">"
   "If you didn't request this, you can safely ignore this email.</p>"
   "</body></html>"))

(defn send-magic-link
  "Send magic link authentication email"
  [email-config base-url token email]
  (let [subject "Your Nextplace login link"
        body    (magic-link-email base-url token email)]
    (send-email email-config email subject body)))

(defmethod ig/init-key :nextplace/email [_ config]
  (log/info "Email service initialized" {:provider (:provider config)})
  config)

(defmethod ig/halt-key! :nextplace/email [_ _]
  (log/info "Email service stopped"))
