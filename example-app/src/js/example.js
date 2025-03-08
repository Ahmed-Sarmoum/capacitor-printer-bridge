import { PrinterBridge } from 'capacitor-printer-bridge';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    PrinterBridge.echo({ value: inputValue })
}
