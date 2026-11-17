import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorBox } from '../components/ErrorBox';
import type { CreateApplicationRequest } from '../types';

function plusDays(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

const EMPTY: CreateApplicationRequest = {
  businessName: '',
  ownerName: '',
  ownerEmail: '',
  phone: '',
  addressLine: '',
  city: '',
  state: 'TX',
  postalCode: '',
  licenseNumber: '',
  licenseState: 'TX',
  licenseHolderName: '',
  licenseExpiration: plusDays(365),
  deliveryRadiusMiles: 10,
  storefrontName: '',
  storefrontSlug: '',
};

/** Synthetic data only. Handy for demos so a full application can be submitted in one click. */
function sample(): CreateApplicationRequest {
  const n = Math.floor(Math.random() * 9000) + 1000;
  const name = `Demo Pharmacy ${n}`;
  return {
    businessName: name,
    ownerName: 'Alex Sample',
    ownerEmail: `owner${n}@example.test`,
    phone: '555-0142',
    addressLine: '200 Elm Street',
    city: 'Plano',
    state: 'TX',
    postalCode: '75024',
    licenseNumber: `TX-${1000000 + n}`,
    licenseState: 'TX',
    licenseHolderName: name,
    licenseExpiration: plusDays(540),
    deliveryRadiusMiles: 12,
    storefrontName: name,
    storefrontSlug: `demo-pharmacy-${n}`,
  };
}

export function NewApplicationPage() {
  const { canWrite } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState<CreateApplicationRequest>(EMPTY);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (!canWrite) return <Navigate to="/applications" replace />;

  const set = <K extends keyof CreateApplicationRequest>(key: K, value: CreateApplicationRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const text = (key: keyof CreateApplicationRequest, label: string, opts: { maxLength?: number; hint?: string } = {}) => (
    <label>
      {label}
      <input
        value={String(form[key] ?? '')}
        onChange={(e) => set(key, e.target.value as never)}
        maxLength={opts.maxLength}
        required={key !== 'phone'}
      />
      {opts.hint && <span className="muted">{opts.hint}</span>}
    </label>
  );

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await api.create({ ...form, phone: form.phone || '' });
      navigate(`/applications/${created.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <h1>New application</h1>
      <p className="muted">
        Records an application as submitted by a pharmacy. Use synthetic data only.{' '}
        <button type="button" className="link-button" onClick={() => setForm(sample())}>
          Fill with sample data
        </button>
      </p>

      <form onSubmit={submit} className="card form-grid">
        <fieldset>
          <legend>Business</legend>
          {text('businessName', 'Business name', { maxLength: 200 })}
          {text('ownerName', 'Owner name', { maxLength: 120 })}
          {text('ownerEmail', 'Owner email', { maxLength: 255 })}
          {text('phone', 'Phone (optional)', { maxLength: 30 })}
          {text('addressLine', 'Street address', { maxLength: 200 })}
          {text('city', 'City', { maxLength: 100 })}
          {text('state', 'State', { maxLength: 2, hint: 'Two letters, like TX' })}
          {text('postalCode', 'ZIP code', { maxLength: 10 })}
        </fieldset>

        <fieldset>
          <legend>License and delivery</legend>
          {text('licenseNumber', 'License number', { maxLength: 40, hint: 'Expected format: TX-1234567' })}
          {text('licenseState', 'License state', { maxLength: 2 })}
          {text('licenseHolderName', 'Name on license', { maxLength: 200 })}
          <label>
            License expiration
            <input
              type="date"
              value={form.licenseExpiration}
              onChange={(e) => set('licenseExpiration', e.target.value)}
              required
            />
          </label>
          <label>
            Delivery radius (miles)
            <input
              type="number"
              min={0}
              step={0.5}
              value={form.deliveryRadiusMiles ?? ''}
              onChange={(e) => set('deliveryRadiusMiles', e.target.value === '' ? null : Number(e.target.value))}
            />
            <span className="muted">Leave empty to see the delivery radius check fail.</span>
          </label>
        </fieldset>

        <fieldset>
          <legend>Storefront</legend>
          {text('storefrontName', 'Storefront name', { maxLength: 120 })}
          {text('storefrontSlug', 'Storefront address', {
            maxLength: 80,
            hint: 'Lowercase letters, numbers and dashes, like maple-pharmacy',
          })}
        </fieldset>

        <ErrorBox error={error} />
        <div className="button-row">
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Submitting...' : 'Submit application'}
          </button>
        </div>
      </form>
    </>
  );
}
