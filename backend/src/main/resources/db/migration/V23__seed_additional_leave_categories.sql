INSERT INTO vacation_types (name, description) VALUES
    ('MATERNITY', 'Maternity Leave'),
    ('PATERNITY', 'Paternity Leave'),
    ('PARENTAL', 'Parental Leave'),
    ('BEREAVEMENT', 'Bereavement Leave'),
    ('ADOPTION', 'Adoption Leave'),
    ('JURY_DUTY', 'Jury Duty'),
    ('MILITARY', 'Military Leave'),
    ('FAMILY_CARE', 'Family Care Leave'),
    ('RELIGIOUS', 'Religious Leave'),
    ('UNPAID_LEAVE', 'Unpaid Leave')
ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description;
