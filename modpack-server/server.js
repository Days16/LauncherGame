const express = require('express');
const multer = require('multer');
const path = require('path');
const cors = require('cors');
const AdmZip = require('adm-zip');
const admin = require('firebase-admin');
const { put, del } = require('@vercel/blob');

const app = express();
const PORT = process.env.PORT || 3000;

// Initialize Firebase Admin (Firestore only)
try {
    const projectId = process.env.FIREBASE_PROJECT_ID;
    const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
    const privateKey = process.env.FIREBASE_PRIVATE_KEY;

    if (projectId && clientEmail && privateKey) {
        admin.initializeApp({
            credential: admin.credential.cert({
                projectId,
                clientEmail,
                privateKey: privateKey.replace(/\\n/g, '\n'),
            })
        });
        console.log('Firebase Admin (Firestore) initialized.');
    }
} catch (err) {
    console.error('Firebase initialization failed:', err);
}

const db = admin.apps.length ? admin.firestore() : null;

app.use(cors());
app.use(express.json());

// Use absolute path for public folder
const publicPath = path.join(__dirname, 'public');
app.use(express.static(publicPath));

const upload = multer({ storage: multer.memoryStorage() });

function getModpackMetadata(buffer, filename) {
    try {
        const zip = new AdmZip(buffer);
        const zipEntries = zip.getEntries();

        const modrinthEntry = zipEntries.find(e => e.entryName === 'modrinth.index.json');
        if (modrinthEntry) {
            const data = JSON.parse(modrinthEntry.getData().toString('utf8'));
            return {
                minecraftVersion: data.dependencies?.minecraft || '1.20.1',
                name: data.name || path.basename(filename, path.extname(filename))
            };
        }

        const curseEntry = zipEntries.find(e => e.entryName === 'manifest.json');
        if (curseEntry) {
            const data = JSON.parse(curseEntry.getData().toString('utf8'));
            return {
                minecraftVersion: data.minecraft?.version || '1.20.1',
                name: data.name || path.basename(filename, path.extname(filename))
            };
        }
    } catch (err) {
        console.error('Error reading zip metadata:', err);
    }
    return {
        minecraftVersion: '1.20.1',
        name: path.basename(filename, path.extname(filename))
    };
}

app.post('/upload', upload.single('modpack'), async (req, res) => {
    if (!req.file) return res.status(400).send('No file uploaded.');
    if (!db) return res.status(500).send('Firestore not configured.');

    try {
        const filename = req.file.originalname;
        const metadata = getModpackMetadata(req.file.buffer, filename);
        const id = filename.replace(/\.(zip|mrpack)$/, '').replace(/\s+/g, '-');

        // Upload to Vercel Blob
        console.log('Uploading to Vercel Blob:', filename);
        const blob = await put(`modpacks/${filename}`, req.file.buffer, {
            access: 'public',
            contentType: req.file.mimetype
        });

        // Save to Firestore
        const modpackData = {
            id,
            name: metadata.name,
            version: '1.0.0',
            minecraftVersion: metadata.minecraftVersion,
            filename,
            description: `Hosted modpack: ${metadata.name}`,
            downloadUrl: blob.url,
            blobUrl: blob.url,
            createdAt: admin.firestore.FieldValue.serverTimestamp()
        };

        await db.collection('modpacks').doc(id).set(modpackData);
        res.send({ message: 'Uploaded!', filename, downloadUrl: blob.url });
    } catch (err) {
        console.error('Upload Error:', err);
        res.status(500).send('Upload failed: ' + err.message);
    }
});

async function getManifestFromFirestore() {
    if (!db) return { modpacks: [] };
    try {
        const snapshot = await db.collection('modpacks').orderBy('createdAt', 'desc').get();
        const modpacks = [];
        snapshot.forEach(doc => modpacks.push(doc.data()));
        return { modpacks };
    } catch (err) {
        console.error('Error fetching from Firestore:', err);
        return { modpacks: [] };
    }
}

app.get('/modpacks.json', async (req, res) => {
    const manifest = await getManifestFromFirestore();
    res.json(manifest);
});

app.get('/manifest', async (req, res) => {
    const manifest = await getManifestFromFirestore();
    res.json(manifest);
});

app.delete('/modpacks/:filename', async (req, res) => {
    if (!db) return res.status(500).send('Firestore not configured.');
    try {
        const filename = req.params.filename;
        const id = filename.replace(/\.(zip|mrpack)$/, '').replace(/\s+/g, '-');

        const doc = await db.collection('modpacks').doc(id).get();
        if (doc.exists) {
            const data = doc.data();
            if (data.blobUrl) {
                await del(data.blobUrl);
            }
        }

        await db.collection('modpacks').doc(id).delete();
        res.send('Deleted.');
    } catch (err) {
        console.error('Delete Error:', err);
        res.status(500).send('Delete failed: ' + err.message);
    }
});

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});

module.exports = app;
