import SwiftUI
import WebKit


struct WebView: UIViewRepresentable {
    let fileName: String?
    let urlString: String?

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let userContentController = WKUserContentController()

        // 注册 JS -> Native 消息通道
        userContentController.add(context.coordinator, name: "getPanelAddresses")
        userContentController.add(context.coordinator, name: "savePanelAddress")
        userContentController.add(context.coordinator, name: "setCurrentPanelAddress")
        userContentController.add(context.coordinator, name: "deletePanelAddress")

        // 注入 CSS 隐藏滚动条
        let css = "body::-webkit-scrollbar { display: none; }"
        let cssJS = """
        var style = document.createElement('style');
        style.innerHTML = '\(css)';
        document.head.appendChild(style);
        """
        let cssScript = WKUserScript(source: cssJS, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        userContentController.addUserScript(cssScript)

        config.userContentController = userContentController

        let webView = WKWebView(frame: .zero, configuration: config)
        context.coordinator.webView = webView

        // 去掉滚动条和弹性
        webView.scrollView.showsHorizontalScrollIndicator = false
        webView.scrollView.showsVerticalScrollIndicator = false
        webView.scrollView.bounces = false

        // 加载本地 HTML 或远程 URL
        if let fileName = fileName,
           let htmlURL = Bundle.main.url(forResource: fileName, withExtension: "html") {
            webView.loadFileURL(htmlURL, allowingReadAccessTo: htmlURL.deletingLastPathComponent())
        } else if let urlString = urlString,
                  let url = URL(string: urlString) {
            webView.load(URLRequest(url: url))
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    class Coordinator: NSObject, WKScriptMessageHandler {
        weak var webView: WKWebView?
        private let storageKey = "panel_addresses"

        
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            switch message.name {
            case "getPanelAddresses":
                handleGetPanelAddresses(message: message)
            case "savePanelAddress":
                handleSavePanelAddress(message: message)
            case "setCurrentPanelAddress":
                handleSetCurrentPanelAddress(message: message)
            case "deletePanelAddress":
                handleDeletePanelAddress(message: message)
            default:
                break
            }
        }

        
        private func handleGetPanelAddresses(message: WKScriptMessage) {
            let callbackName: String
            if let cb = message.body as? String, !cb.isEmpty {
                callbackName = cb
            } else {
                callbackName = "setPanelAddresses"
            }

            let addresses = loadAddressesJSONString()
            let js = "window.\(callbackName)(\(addresses));"
            evaluate(js: js)
        }

        private func handleSavePanelAddress(message: WKScriptMessage) {
            var name: String?
            var address: String?

            if let dict = message.body as? [String: Any] {
                name = dict["name"] as? String
                address = dict["address"] as? String
            } else if let arr = message.body as? [Any], arr.count >= 2 {
                name = arr[0] as? String
                address = arr[1] as? String
            } else if let jsonString = message.body as? String,
                      let data = jsonString.data(using: .utf8),
                      let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                name = dict["name"] as? String
                address = dict["address"] as? String
            }

            guard let panelName = name, let panelAddress = address, !panelName.isEmpty, !panelAddress.isEmpty else {
                return
            }

            var array = loadAddressesArray()
            let newItem: [String: Any] = [
                "name": panelName,
                "address": panelAddress,
                "inx": false
            ]
            array.append(newItem)
            saveAddressesArray(array)

            let js = "window.setPanelAddresses(\(toJSONString(array)));"
            evaluate(js: js)
        }

        private func handleSetCurrentPanelAddress(message: WKScriptMessage) {
            var name: String?
            if let str = message.body as? String {
                name = str
            } else if let dict = message.body as? [String: Any] {
                name = dict["name"] as? String
            }

            guard let targetName = name else { return }

            var array = loadAddressesArray()
            for i in 0..<array.count {
                var obj = array[i]
                if let n = obj["name"] as? String, n == targetName {
                    obj["inx"] = true
                } else {
                    obj["inx"] = false
                }
                array[i] = obj
            }
            saveAddressesArray(array)
            let js = "window.setPanelAddresses(\(toJSONString(array)));"
            evaluate(js: js)
        }

        private func handleDeletePanelAddress(message: WKScriptMessage) {
            var name: String?
            if let str = message.body as? String {
                name = str
            } else if let dict = message.body as? [String: Any] {
                name = dict["name"] as? String
            }
            guard let targetName = name else { return }

            let oldArray = loadAddressesArray()
            let newArray = oldArray.filter { obj in
                guard let n = obj["name"] as? String else { return true }
                return n != targetName
            }
            saveAddressesArray(newArray)
            let js = "window.setPanelAddresses(\(toJSONString(newArray)));"
            evaluate(js: js)
        }

        private func loadAddressesJSONString() -> String {
            let defaults = UserDefaults.standard
            if let s = defaults.string(forKey: storageKey), !s.isEmpty {
                return s
            }
            return "[]"
        }

        private func loadAddressesArray() -> [[String: Any]] {
            let s = loadAddressesJSONString()
            guard let data = s.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                return []
            }
            return json
        }

        private func saveAddressesArray(_ array: [[String: Any]]) {
            let jsonString = toJSONString(array)
            let defaults = UserDefaults.standard
            defaults.set(jsonString, forKey: storageKey)
        }

        private func toJSONString(_ array: [[String: Any]]) -> String {
            if let data = try? JSONSerialization.data(withJSONObject: array, options: []),
               let s = String(data: data, encoding: .utf8) {
                return s
            }
            return "[]"
        }

        private func evaluate(js: String) {
            DispatchQueue.main.async { [weak self] in
                self?.webView?.evaluateJavaScript(js, completionHandler: nil)
            }
        }
    }
}



struct ContentView: View {
    var body: some View {
        //WebView(fileName: nil, urlString: "http://192.168.100.9:3000")
        WebView(fileName: "index", urlString: nil)
    }
}


#Preview {
    ContentView()
}
