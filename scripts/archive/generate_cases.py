import json
import random

categories = [
    {"name": "Retrieval", "weight": 25, "templates": [
        "What was my last {item}?",
        "Find the document about {item}.",
        "When did I purchase the {item}?",
        "Where is the receipt for the {item}?"
    ], "items": ["car service", "laptop", "insurance", "flight to Dubai", "monthly rent", "tax return", "electricity bill", "water bill", "vet visit"]},
    
    {"name": "Factual", "weight": 25, "templates": [
        "What is the exact expiry date of {item}?",
        "How much did I pay for {item}?",
        "Who is the provider for {item}?",
        "What is the policy number for {item}?"
    ], "items": ["Allianz insurance", "Vodafone internet", "Emirates flight", "gym membership", "Netflix subscription", "Amazon Prime", "car registration"]},
    
    {"name": "Tools", "weight": 20, "templates": [
        "Remind me one month before my {item} expires.",
        "Set an alert for the {item} renewal.",
        "Create a collection for all {item} documents."
    ], "items": ["insurance", "visa", "passport", "driving license", "tax deadline", "health checkup"]},
    
    {"name": "Cross-document", "weight": 20, "templates": [
        "What documents relate to my {item}?",
        "Summarize all expenses for {item}.",
        "How many times did I renew {item} in 2026?"
    ], "items": ["Duster", "apartment", "health insurance", "freelance work", "vacation", "pet dog"]},
    
    {"name": "Hallucination", "weight": 10, "templates": [
        "What is my {item}?",
        "Who do I owe money for {item}?",
        "When does my {item} expire?"
    ], "items": ["Emirates loyalty number", "private jet lease", "Mars plot deed", "Bugatti insurance", "Rolex warranty"]}
]

cases = []
for c in categories:
    count = 20 # 20 per category = 100 total
    for _ in range(count):
        item = random.choice(c["items"])
        template = random.choice(c["templates"])
        query = template.format(item=item)
        
        expected = []
        if c["name"] == "Hallucination":
            expected = ["I couldn't find", "not found", "no record"]
        elif c["name"] == "Tools":
            expected = ["propose_reminder" if "Remind" in template or "alert" in template else "create_collection"]
        else:
            expected = [item.split()[0]]
            
        case = {
            "category": c["name"],
            "query": query,
            "expectedSubstrings": expected if c["name"] != "Tools" else [],
            "expectedProposals": expected if c["name"] == "Tools" else []
        }
        cases.append(case)

with open(r'd:\Vault Brain\core\ai\rag\src\test\resources\benchmark_cases.json', 'w', encoding='utf-8') as f:
    json.dump(cases, f, indent=2)
