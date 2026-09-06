import json
import random
from datetime import datetime, timedelta

def generate_dataset():
    random.seed(42)
    documents = []
    
    # 1. Receipts (15 cases)
    for i in range(15):
        merchant = random.choice(["Coffee Shop", "Grocery Store", "Electronics Mart", "Pharmacy", "Restaurant", "Gas Station"])
        amount = round(random.uniform(5.0, 500.0), 2)
        days_ago = random.randint(1, 30)
        date = (datetime.now() - timedelta(days=days_ago)).strftime("%Y-%m-%d")
        
        doc = {
            "id": f"receipt_{i}",
            "source_type": "synthetic",
            "language": ["en"],
            "document_type": "RECEIPT",
            "primary_lens": "MONEY",
            "ground_truth_text": f"{merchant}\nDate: {date}\nItem 1  $10.00\nItem 2  ${amount - 10}\nTotal: ${amount:.2f}",
            "fields": {
                "merchant": merchant,
                "total": f"{amount:.2f}",
                "currency": "USD",
                "purchase_date": date
            },
            "critical_fields": ["total", "merchant", "purchase_date"]
        }
        documents.append(doc)
        
    # 2. Passports (10 cases)
    for i in range(10):
        first_name = random.choice(["JOHN", "EMILY", "MICHAEL", "SARAH", "DAVID"])
        last_name = random.choice(["SMITH", "DOE", "JOHNSON", "BROWN", "TAYLOR"])
        doc_num = f"P{random.randint(1000000, 9999999)}"
        exp_year = random.randint(2026, 2035)
        dob = f"19{random.randint(70, 99)}-0{random.randint(1, 9)}-1{random.randint(0, 9)}"
        exp = f"{exp_year}-0{random.randint(1, 9)}-1{random.randint(0, 9)}"
        
        doc = {
            "id": f"passport_{i}",
            "source_type": "synthetic",
            "language": ["en"],
            "document_type": "PASSPORT",
            "primary_lens": "BUREAUCRACY",
            "ground_truth_text": f"PASSPORT\nName: {first_name} {last_name}\nPassport No: {doc_num}\nDOB: {dob}\nExpiry: {exp}\nMRZ:\nP<USA{last_name}<<{first_name}<<<<<<<<<<<<\n{doc_num}<0USA{dob.replace('-','')[2:]}M{exp.replace('-','')[2:]}<<<<<<<<<<<<<",
            "fields": {
                "name": f"{first_name} {last_name}",
                "document_number": doc_num,
                "dob": dob,
                "expiry_date": exp,
                "document_type": "passport"
            },
            "critical_fields": ["document_number", "expiry_date", "dob"]
        }
        documents.append(doc)

    # 3. Invoices (15 cases)
    for i in range(15):
        issuer = random.choice(["Acme Corp", "Tech Supply", "Design Studio", "Hosting Provider"])
        invoice_num = f"INV-{random.randint(1000, 9999)}"
        amount = round(random.uniform(100.0, 5000.0), 2)
        due = f"2026-10-{random.randint(10, 28)}"
        
        doc = {
            "id": f"invoice_{i}",
            "source_type": "synthetic",
            "language": ["en"],
            "document_type": "INVOICE",
            "primary_lens": "MONEY",
            "ground_truth_text": f"{issuer}\nINVOICE\nInvoice Number: {invoice_num}\nDue Date: {due}\nAmount Due: €{amount:.2f}",
            "fields": {
                "merchant": issuer,
                "invoice_number": invoice_num,
                "due_date": due,
                "total": f"{amount:.2f}",
                "currency": "EUR"
            },
            "critical_fields": ["invoice_number", "due_date", "total"]
        }
        documents.append(doc)
        
    # 4. Tickets (10 cases)
    for i in range(10):
        airline = random.choice(["Emirates", "Delta", "British Airways", "Lufthansa"])
        pnr = "".join(random.choices("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", k=6))
        flight = f"{airline[:2].upper()}{random.randint(100, 999)}"
        date = f"2026-11-{random.randint(10, 28)}"
        
        doc = {
            "id": f"ticket_{i}",
            "source_type": "synthetic",
            "language": ["en"],
            "document_type": "TICKET",
            "primary_lens": "TRAVEL",
            "ground_truth_text": f"BOARDING PASS\n{airline}\nPNR: {pnr}\nFlight: {flight}\nDate: {date}\nSeat: 12A",
            "fields": {
                "pnr": pnr,
                "flight_number": flight,
                "date": date,
                "seat": "12A"
            },
            "critical_fields": ["pnr", "flight_number", "date"]
        }
        documents.append(doc)

    with open('evaluation/datasets/nemory_gold/synthetic_documents.json', 'w') as f:
        json.dump(documents, f, indent=2)
        
    print(f"Generated {len(documents)} synthetic document cases.")

if __name__ == '__main__':
    generate_dataset()
