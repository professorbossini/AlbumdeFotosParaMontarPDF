package br.com.bossini.albumdefotosparamontarpdf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //na classe MainActivity
    private RecyclerView fotosRecyclerView;
    private List<Bitmap> fotos;
    private FloatingActionButton fab;

    //classe aninhada de MainActivity, observe a herança
    private static class FotosViewHolder extends RecyclerView.ViewHolder{
        //raiz da árvore cujos componentes são guardados pelo ViewHolder
        public View view;
        //somente um ImageView...
        private ImageView fotoImageView;
        //repassa a referência da raiz e faz a busca uma só vez por ViewHolder
        public FotosViewHolder(View view) {
            super(view);
            this.view = view;
            this.fotoImageView = view.findViewById(R.id.fotoImageView);
        }
    }

    //interna de MainActivity. Observe a herança
    private class FotosAdapter extends RecyclerView.Adapter<FotosViewHolder> {
        //para pegar o inflador de layout
        private Context context;
        //fonte de dados
        private List <Bitmap> fotos;
        public FotosAdapter (Context context, List <Bitmap> fotos){
            this.context = context;
            this.fotos = fotos;
        }
        //o que ocorre quando um ViewHolder for criado?
        public FotosViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(context).inflate(R.layout.fotos_layout, parent, false);
            return new FotosViewHolder(v);
        }
        //o que ocorre quando um viewholder já existe e precisa ter seu conteúdo atribuído?
        public void onBindViewHolder(@NonNull FotosViewHolder holder, int position) {
            Bitmap foto = fotos.get(position);
            holder.fotoImageView.setImageBitmap(foto);
        }
        public int getItemCount() {
            return fotos.size();
        }
    };
    private void setUpRecyclerView (){
        //busca na árvore
        fotosRecyclerView = findViewById(R.id.fotosRecyclerView);
        //inicializa a fonte de dados
        fotos = new ArrayList<Bitmap>();
        //vincula o adapter
        fotosRecyclerView.setAdapter(new FotosAdapter(this, fotos));
        //diz qual o gerenciador de layout
        //neste caso temos um GridLayout com 2 colunas
        fotosRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
    }
    private void goTakePicture (){
        Intent takePicIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(takePicIntent, REQUEST_TAKE_PICTURE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


        setUpRecyclerView();
        //no método onCreate, depois de inflar a view
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(fabListener);
    }
    private View.OnClickListener fabListener = new View.OnClickListener() {
        public void onClick(View v) {
            //verifica se já tem permissão
            if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
                //verifica se deve explicar
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, android.Manifest.permission.CAMERA)){
                    Toast.makeText(MainActivity.this, getString(R.string.explicacao_permissao_camera), Toast.LENGTH_SHORT).show();
                }
                //se não tem permissão, pede
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
            }
            else{
                //se já tem, tira foto
                goTakePicture();
            }
        }
    };
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case REQUEST_PERMISSION_CAMERA:
                //usuário disse sim
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    goTakePicture();
                }
                //usuário disse não
                else{
                    Toast.makeText(this, getString(R.string.explicacao_permissao_camera),
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
    //constante de MainActivity
    private static final int REQUEST_PERMISSION_CAMERA = 2001;
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode){
            case REQUEST_TAKE_PICTURE:
                //usuário tirou foto
                if (resultCode == Activity.RESULT_OK){
                    //pega a foto
                    Bitmap foto = (Bitmap) data.getExtras().get("data");
                    //faz upload para o Firebase Storage
                    uploadImage(foto);
                }
                else{
                    Toast.makeText(this, getString(R.string.foto_nao_confirmada), Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
    //constante de MainActivity
    private static final int REQUEST_TAKE_PICTURE = 1001;
    //referência ao diretório images
    private StorageReference imagesReference;
    //referência para gerar nomes de arquivos únicos
    private DatabaseReference fileNameGenerator;
    //referência para guardar as urls das fotos cujo upload foi realizado
    private DatabaseReference urlsReference;

    private void setUpFirebase(){
        //singleton do Storage
        final FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
        //referência para a raiz do Storage
        final StorageReference storageRootReference = firebaseStorage.getReference();
        //referência para a pasta images, onde todas as fotos ficarão
        this.imagesReference = storageRootReference.child("images");
        //referência para o singleton do Database realtime
        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        //referência para o gerador de nomes de arquivos
        this.fileNameGenerator = firebaseDatabase.getReference("image_names");
        //referência para onde os nomes ficam associados a urls
        this.urlsReference = firebaseDatabase.getReference("urls");
        //registra um observador que é ativado de imediato, ele faz o download das fotos
        this.urlsReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //limpa fotos anteriores
                fotos.clear();
                //para cada foto
                for (DataSnapshot filho : dataSnapshot.getChildren()){
                    String url = filho.getValue() + PNG_EXTENSION;
                    StorageReference aux = firebaseStorage.getReferenceFromUrl(url);
                    //baixa a foto de forma assincrona
                    aux.getBytes(UM_MEGA).addOnSuccessListener(new OnSuccessListener<byte[]>() {
                        @Override
                        public void onSuccess(byte[] bytes) {
                            //quando terminar atualiza a RecyclerView
                            updateRecyclerViewList(Utils.toBitmap(bytes));
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            //se falhar
                            Toast.makeText(MainActivity.this, getString(R.string.falha_no_download),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
            public void onCancelled(DatabaseError databaseError) {
                //se não der certo a conexão inicial
                Toast.makeText(MainActivity.this, getString(R.string.falha_conexao_fb),
                        Toast.LENGTH_SHORT).show();
                Log.i("teste",databaseError.getDetails() );
            }
        });
    }
    //constantes de MainActivity
    private static final String PNG_EXTENSION = ".png";
    private static final long UM_MEGA = 1000 * 1000;

    protected void onStart() {
        super.onStart();
         setUpFirebase();
    }

    private void updateRecyclerViewList(Bitmap foto){
        fotos.add(foto);
        fotosRecyclerView.getAdapter().notifyDataSetChanged();
    }


    private void uploadImage (final Bitmap image){

        //gera um nomoe aleatório
        final String chave = this.fileNameGenerator.push().getKey();
        //armazena no storage com extensão
        StorageReference storageReference = this.imagesReference.child(chave + PNG_EXTENSION);
        byte [] data = Utils.toByteArray (image);
        UploadTask task = storageReference.putBytes(data);
        task.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                updateRecyclerViewList(image);
                Uri downloadUrl = taskSnapshot.getDownloadUrl();
                //no database a chave tem que ser sem extensão, por causa do ponto
                saveURLForDownload (downloadUrl, chave);
                Toast.makeText(MainActivity.this, getString(R.string.sucesso_no_upload),
                        Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(new OnFailureListener() {
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, getString(R.string.falha_no_upload), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }

    private void saveURLForDownload (Uri downloadURL, String name){
        this.urlsReference.child(name).setValue(downloadURL.toString());
    }
}
