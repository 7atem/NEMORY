import json, re, subprocess

w = subprocess.run(['cygpath', '-w', '/tmp/strings.json'], capture_output=True, text=True).stdout.strip()
d = json.load(open(w, encoding='utf-8'))

cur_path = 'feature/capture/src/main/res/values/strings.xml'
cur = open(cur_path, encoding='utf-8').read()
kept = set(re.findall(r'name="(experience_[a-z_0-9]+)"', cur))

def clean(s):
    s = s.strip()
    # strip stray leading punctuation artifacts from pool decoding
    s = re.sub(r'^[\s&!#"$\'()\t\n\r]+', '', s)
    s = s.rstrip('\n').strip()
    return s

# hand-written title/hint for ids not present in the AAB resource table
manual = {
    'credit_card_statement': ('Credit card statement', 'Card transactions and balance.'),
    'loan_payment': ('Loan payment', 'Installment, interest, and balance.'),
    'grocery_list': ('Grocery list', 'Items to buy at the store.'),
    'pet_expense': ('Pet expense', 'Vet visit, food, or supplies.'),
    'tuition_fee': ('Tuition fee', 'School or course payment.'),
    'dental_visit': ('Dental visit', 'Dentist appointment or treatment.'),
    'optical_prescription': ('Optical prescription', 'Glasses or lens prescription.'),
    'allergy_record': ('Allergy record', 'Allergies and reactions.'),
    'physiotherapy_plan': ('Physiotherapy plan', 'Exercises and sessions.'),
    'mental_health_note': ('Mental health note', 'Therapy or wellness note.'),
    'blood_donation': ('Blood donation', 'Donation date and center.'),
    'ferry_ticket': ('Ferry ticket', 'Route, date, and seat.'),
    'travel_insurance': ('Travel insurance', 'Coverage and policy number.'),
    'travel_checklist': ('Travel checklist', 'Packing or preparation list.'),
    'airport_lounge': ('Airport lounge', 'Lounge access pass.'),
    'birth_certificate': ('Birth certificate', 'Official birth record.'),
    'marriage_certificate': ('Marriage certificate', 'Official marriage record.'),
    'notarized_document': ('Notarized document', 'Certified or notarized paper.'),
    'tax_residency_certificate': ('Tax residency certificate', 'Proof of tax residency.'),
    'professional_license': ('Professional license', 'License number and expiry.'),
    'property_deed': ('Property deed', 'Ownership document.'),
    'mortgage_statement': ('Mortgage statement', 'Loan balance and payments.'),
    'home_insurance_policy': ('Home insurance policy', 'Coverage and policy number.'),
    'appliance_registration': ('Appliance registration', 'Product registration details.'),
    'utility_setup': ('Utility setup', 'New utility account or transfer.'),
    'car_loan': ('Car loan', 'Loan terms and payments.'),
    'toll_receipt': ('Toll receipt', 'Road or bridge toll.'),
    'parking_permit': ('Parking permit', 'Permit number and validity.'),
    'car_inspection': ('Car inspection', 'Inspection date and result.'),
    'order_confirmation': ('Order confirmation', 'Order number and items.'),
    'shipping_tracking': ('Shipping tracking', 'Tracking number and status.'),
    'loyalty_card': ('Loyalty card', 'Membership or points card.'),
    'gift_receipt': ('Gift receipt', 'Receipt without price.'),
    'wishlist': ('Wishlist', 'Thing you want to buy.'),
    'podcast': ('Podcast', 'Episode or show to listen to.'),
    'audiobook': ('Audiobook', 'Audiobook to listen to.'),
    'ebook': ('Ebook', 'Ebook to read.'),
    'online_course': ('Online course', 'Course to take.'),
    'concert_ticket': ('Concert ticket', 'Event date and seat.'),
    'museum_ticket': ('Museum ticket', 'Visit date and entry.'),
    'theater_ticket': ('Theater ticket', 'Show date and seat.'),
    'todo_list': ('To-do list', 'Tasks to complete.'),
    'habit_tracker': ('Habit tracker', 'Daily habit to track.'),
    'goal': ('Goal', 'Personal goal and progress.'),
    'project_plan': ('Project plan', 'Milestones and tasks.'),
    'journal': ('Journal', 'Personal journal entry.'),
    'gratitude_log': ('Gratitude log', 'Things you are grateful for.'),
    'phone_plan': ('Phone plan', 'Carrier plan and renewal.'),
    'internet_plan': ('Internet plan', 'ISP plan and renewal.'),
    'gym_membership': ('Gym membership', 'Membership and renewal.'),
    'streaming_service': ('Streaming service', 'Subscription and renewal.'),
    'cloud_storage': ('Cloud storage', 'Storage plan and renewal.'),
    'cleaning_service': ('Cleaning service', 'Service schedule and cost.'),
    'fake_invoice': ('Fake invoice', 'Invoice that looks fraudulent.'),
    'fake_check': ('Fake check', 'Check that looks fraudulent.'),
    'advance_fee_fraud': ('Advance-fee fraud', 'Requests upfront payment.'),
    'lottery_scam': ('Lottery scam', 'Fake prize or winnings.'),
    'tech_support_scam': ('Tech support scam', 'Fake support or virus alert.'),
    'romance_scam': ('Romance scam', 'Online relationship scam.'),
}

def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

out = []
for key, val in sorted(d.items()):
    if key in kept:
        continue
    v = clean(val)
    if v:
        out.append((key, v))
for snake, (title, hint) in manual.items():
    out.append(('experience_' + snake, title))
    out.append(('experience_' + snake + '_hint', hint))

lines = ''.join('    <string name="%s">%s</string>\n' % (k, esc(v)) for k, v in out)
cur = cur.replace('</resources>', lines + '</resources>')
open(cur_path, 'w', encoding='utf-8', newline='\n').write(cur)
print('restored strings:', len(out))
